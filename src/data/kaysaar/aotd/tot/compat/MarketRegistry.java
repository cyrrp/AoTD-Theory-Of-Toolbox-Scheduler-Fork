package data.kaysaar.aotd.tot.compat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AoTD-owned market registry and coalescing dirty queue.
 *
 * <p>The market-id lookup is lock-free because it is used from synchronous
 * price APIs. Registry/queue transitions remain serialized under one short-lived
 * lock. A market has at most one queued or in-flight ticket; later events are
 * merged into one replacement revision.</p>
 */
public final class MarketRegistry {
    public static final int DIRTY_TIME_DELIVERED = 1 << 4;
    public static final int DIRTY_VALUE_STATE = 1 << 5;
    public static final int DIRTY_PRICE = 1 << 6;
    public static final int DIRTY_STOCKPILE = 1 << 7;
    public static final int DIRTY_ACCESSIBILITY = 1 << 8;
    public static final int DIRTY_TRADE = 1 << 9;
    public static final int DIRTY_GLOBAL_REVISION = 1 << 10;
    public static final int DIRTY_INITIAL_REGISTRATION = 1 << 11;

    /** Bits fully satisfied by the Stage 6 local price/stockpile commit. */
    public static final int PRICE_WORK_MASK = DIRTY_PRICE | DIRTY_STOCKPILE;

    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_VISIBLE = 10;
    public static final int PRIORITY_PLAYER = 20;
    public static final int PRIORITY_IMMEDIATE = 30;

    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, Object> MARKETS_BY_ID =
            new ConcurrentHashMap<>();
    private static final Map<String, MarketEconomyState> STATES_BY_ID = new HashMap<>();
    private static final IdentityHashMap<Object, String> IDS_BY_MARKET = new IdentityHashMap<>();
    private static final ArrayDeque<MarketEconomyState> URGENT = new ArrayDeque<>();
    private static final ArrayDeque<MarketEconomyState> NORMAL = new ArrayDeque<>();

    private static long registryGeneration;
    private static long dirtyGeneration;
    private static long claimedTickets;
    private static long committedTickets;
    private static long staleTickets;
    private static long coalescedEvents;
    private static long unknownDeliveryEvents;
    private static long unknownMutationEvents;
    private static long duplicateDeliveryEvents;
    private static long replacedMarketIdentities;
    private static long lifecycleRejects;
    private static long priceClaims;
    private static long priceCommits;
    private static long stalePriceResults;
    private static int urgentBurst;

    private MarketRegistry() {}

    /** Clears loader-local runtime state when a new economy/save is installed. */
    public static void clear() {
        synchronized (LOCK) {
            MARKETS_BY_ID.clear();
            STATES_BY_ID.clear();
            IDS_BY_MARKET.clear();
            URGENT.clear();
            NORMAL.clear();
            registryGeneration = nextPositive(registryGeneration);
            dirtyGeneration = 0L;
            claimedTickets = 0L;
            committedTickets = 0L;
            staleTickets = 0L;
            coalescedEvents = 0L;
            unknownDeliveryEvents = 0L;
            unknownMutationEvents = 0L;
            duplicateDeliveryEvents = 0L;
            replacedMarketIdentities = 0L;
            lifecycleRejects = 0L;
            priceClaims = 0L;
            priceCommits = 0L;
            stalePriceResults = 0L;
            urgentBurst = 0;
        }
    }

    public static void registerMarket(String marketId, Object market) {
        if (marketId == null || marketId.isEmpty() || market == null) return;
        synchronized (LOCK) {
            Object previous = MARKETS_BY_ID.put(marketId, market);
            if (previous != null && previous != market) {
                IDS_BY_MARKET.remove(previous);
                replacedMarketIdentities++;
            }

            String previousId = IDS_BY_MARKET.put(market, marketId);
            if (previousId != null && !previousId.equals(marketId)) {
                MARKETS_BY_ID.remove(previousId, market);
                MarketEconomyState displaced = STATES_BY_ID.remove(previousId);
                removeQueuedLocked(displaced);
            }

            MarketEconomyState state = STATES_BY_ID.get(marketId);
            boolean created = state == null;
            boolean replaced = state != null && state.getMarket() != market;
            if (replaced) {
                removeQueuedLocked(state);
                state = null;
            }
            if (state == null) {
                state = new MarketEconomyState(marketId, market);
                STATES_BY_ID.put(marketId, state);
                created = true;
            } else {
                state.setMarket(market);
            }

            long delivered = SchedulerBridge.getDeliveredMarketGeneration(market);
            long structural = SchedulerBridge.getMarketStructuralGeneration(market);
            if (delivered > state.getDeliveredGeneration()) state.setDeliveredGeneration(delivered);
            if (structural > state.getStructuralGeneration()) state.setStructuralGeneration(structural);
            registryGeneration = nextPositive(registryGeneration);

            if (created) {
                markDirtyLocked(state,
                        SchedulerBridge.DIRTY_STRUCTURE
                                | SchedulerBridge.DIRTY_INDUSTRIES
                                | SchedulerBridge.DIRTY_CONDITIONS
                                | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                                | DIRTY_INITIAL_REGISTRATION,
                        PRIORITY_NORMAL);
            }
        }
    }

    public static void unregisterMarket(String marketId, Object market) {
        synchronized (LOCK) {
            String resolved = marketId;
            if ((resolved == null || resolved.isEmpty()) && market != null) {
                resolved = IDS_BY_MARKET.get(market);
            }
            if (resolved == null) return;

            Object mapped = MARKETS_BY_ID.get(resolved);
            // A late unregister for an old object must not remove its replacement.
            if (market != null && mapped != null && mapped != market) {
                IDS_BY_MARKET.remove(market);
                return;
            }

            Object removed = MARKETS_BY_ID.remove(resolved);
            if (removed != null) IDS_BY_MARKET.remove(removed);
            if (market != null) IDS_BY_MARKET.remove(market);
            MarketEconomyState state = STATES_BY_ID.remove(resolved);
            removeQueuedLocked(state);
            registryGeneration = nextPositive(registryGeneration);
        }
    }

    /** Hot synchronous lookup: no registry monitor is acquired. */
    public static Object lookupMarket(String marketId) {
        return marketId == null ? null : MARKETS_BY_ID.get(marketId);
    }

    public static MarketEconomyState stateForId(String marketId) {
        synchronized (LOCK) {
            return STATES_BY_ID.get(marketId);
        }
    }

    public static void onMarketTimeDelivered(
            Object market, long deliveredGeneration, long deliverySequence,
            float deliveredAmount) {
        if (market == null) return;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) {
                unknownDeliveryEvents++;
                return;
            }

            boolean changed = false;
            if (deliveredGeneration > state.getDeliveredGeneration()) {
                state.setDeliveredGeneration(deliveredGeneration);
                changed = true;
            } else if (deliveredGeneration <= 0L
                    && deliverySequence > state.getLastDeliverySequence()) {
                state.setDeliveredGeneration(nextPositive(state.getDeliveredGeneration()));
                changed = true;
            }
            if (deliverySequence > state.getLastDeliverySequence()) {
                state.setLastDeliverySequence(deliverySequence);
                changed = true;
            }
            if (!changed) {
                duplicateDeliveryEvents++;
                return;
            }

            markDirtyLocked(state,
                    DIRTY_TIME_DELIVERED | SchedulerBridge.DIRTY_DERIVED_ECONOMY,
                    PRIORITY_NORMAL);
        }
    }

    public static void onMarketMutationCommitted(
            Object market, int dirtyMask, long sourceGeneration,
            long structuralGeneration) {
        if (market == null) return;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) {
                unknownMutationEvents++;
                return;
            }
            if (structuralGeneration > state.getStructuralGeneration()) {
                state.setStructuralGeneration(structuralGeneration);
            } else if (structuralGeneration <= 0L
                    && (dirtyMask & SchedulerBridge.DIRTY_STRUCTURE) != 0) {
                // No-agent fallback: keep the fork-local stale-result contract valid.
                state.setStructuralGeneration(nextPositive(state.getStructuralGeneration()));
            }
            if (sourceGeneration > state.getLastSourceGeneration()) {
                state.setLastSourceGeneration(sourceGeneration);
            }
            markDirtyLocked(state, dirtyMask, PRIORITY_IMMEDIATE);
        }
    }

    public static void markDirty(Object market, int dirtyMask, int priorityHint) {
        if (market == null || dirtyMask == 0) return;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state != null) markDirtyLocked(state, dirtyMask, priorityHint);
        }
    }

    public static int getMarketDirtyMask(Object market) {
        if (market == null) return 0;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state == null ? 0 : state.getDirtyMask();
        }
    }

    public static long getMarketDirtyGeneration(Object market) {
        if (market == null) return 0L;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state == null ? 0L : state.getDirtyGeneration();
        }
    }

    public static long getMarketStructuralGeneration(Object market) {
        if (market == null) return 0L;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state == null ? 0L : state.getStructuralGeneration();
        }
    }

    /** True while local authoritative/derived state still needs a commit. */
    public static boolean needsDerivedRefresh(Object market) {
        if (market == null) return true;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) return true;
            return state.getDirtyMask() != 0
                    || state.isQueued()
                    || state.isSnapshotBuilding()
                    || state.isRunning()
                    || state.isResultReady()
                    || state.getDerivedGeneration() < state.getDirtyGeneration();
        }
    }

    /** Whether the live condition/industry modifiers must be materialized again. */
    public static boolean needsMaterializedReconciliation(Object market) {
        int mask = getMarketDirtyMask(market);
        int materializedMask = SchedulerBridge.DIRTY_STRUCTURE
                | SchedulerBridge.DIRTY_INDUSTRIES
                | SchedulerBridge.DIRTY_CONDITIONS
                | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                | DIRTY_TIME_DELIVERED
                | DIRTY_VALUE_STATE
                | DIRTY_INITIAL_REGISTRATION;
        return (mask & materializedMask) != 0;
    }

    /** Whether the authoritative local trade snapshot can have changed. */
    public static boolean needsTradeSnapshot(Object market) {
        int mask = getMarketDirtyMask(market);
        int tradeMask = SchedulerBridge.DIRTY_STRUCTURE
                | SchedulerBridge.DIRTY_INDUSTRIES
                | SchedulerBridge.DIRTY_CONDITIONS
                | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                | DIRTY_TIME_DELIVERED
                | DIRTY_VALUE_STATE
                | DIRTY_ACCESSIBILITY
                | DIRTY_TRADE
                | DIRTY_GLOBAL_REVISION
                | DIRTY_INITIAL_REGISTRATION;
        return (mask & tradeMask) != 0;
    }

    /** Whether the market needs a new pure price/stockpile computation. */
    public static boolean needsPriceRefresh(Object market) {
        if (market == null) return true;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) return true;
            return (state.getDirtyMask() & PRICE_WORK_MASK) != 0
                    || state.getPriceGeneration() < state.getDirtyGeneration();
        }
    }

    /**
     * Claims one specific market for Stage 6 price work. Unlike the general
     * queue claim this preserves market ordering selected by the economy task.
     */
    public static WorkTicket claimMarketForPrice(Object market) {
        if (market == null) return null;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null || state.isQuarantined()
                    || state.isSnapshotBuilding() || state.isRunning()
                    || state.isResultReady()) {
                return null;
            }
            if ((state.getDirtyMask() & PRICE_WORK_MASK) == 0
                    && state.getPriceGeneration() >= state.getDirtyGeneration()) {
                return null;
            }
            removeQueuedLocked(state);
            int capturedMask = state.getDirtyMask();
            int capturedPriority = state.getPriorityHint();
            long capturedSince = state.getFirstDirtyNanos();
            long capturedDirtyGeneration = state.getDirtyGeneration();
            state.setDirtyMask(0);
            state.setPriorityHint(PRIORITY_NORMAL);
            state.setFirstDirtyNanos(0L);
            state.setLastDirtyNanos(0L);
            state.setSnapshotBuilding(true);
            claimedTickets++;
            priceClaims++;
            return new WorkTicket(
                    state.getMarketId(), state.getMarket(),
                    state.getDeliveredGeneration(), state.getStructuralGeneration(),
                    capturedDirtyGeneration, capturedMask,
                    capturedPriority, capturedSince);
        }
    }

    /**
     * Completes only the local price/stockpile portion of a market revision.
     * Reconciliation/trade bits remain dirty for AoTDUpdateMarketAgainTask.
     */
    public static boolean commitPriceDerived(WorkTicket ticket, long computeNanos) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return false;
            if (!state.isResultReady()) {
                lifecycleRejects++;
                return false;
            }
            boolean current = matchesGenerationsLocked(state, ticket);
            state.setResultReady(false);
            if (current) {
                state.setPriceGeneration(Math.max(
                        state.getPriceGeneration(), ticket.dirtyGeneration));
                state.setLastComputeNanos(Math.max(0L, computeNanos));
                int remaining = ticket.dirtyMask & ~PRICE_WORK_MASK;
                if (remaining != 0) {
                    state.setDirtyMask(state.getDirtyMask() | remaining);
                    if (ticket.priorityHint > state.getPriorityHint()) {
                        state.setPriorityHint(ticket.priorityHint);
                    }
                    if (ticket.dirtySinceNanos > 0L
                            && (state.getFirstDirtyNanos() == 0L
                            || ticket.dirtySinceNanos < state.getFirstDirtyNanos())) {
                        state.setFirstDirtyNanos(ticket.dirtySinceNanos);
                    }
                    if (state.getLastDirtyNanos() == 0L) {
                        state.setLastDirtyNanos(System.nanoTime());
                    }
                } else if (state.getDirtyMask() == 0) {
                    state.setDerivedGeneration(Math.max(
                            state.getDerivedGeneration(), ticket.dirtyGeneration));
                }
                committedTickets++;
                priceCommits++;
            } else {
                staleTickets++;
                stalePriceResults++;
                restoreTicketDirtyLocked(state, ticket);
            }
            enqueueIfIdleLocked(state);
            return current;
        }
    }

    public static void markDirtyById(String marketId, int dirtyMask, int priorityHint) {
        if (marketId == null || dirtyMask == 0) return;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(marketId);
            if (state != null) markDirtyLocked(state, dirtyMask, priorityHint);
        }
    }

    public static WorkTicket[] claimDirtyBatch(int maxMarkets) {
        if (maxMarkets <= 0) return new WorkTicket[0];
        synchronized (LOCK) {
            int count = Math.min(maxMarkets, URGENT.size() + NORMAL.size());
            if (count <= 0) return new WorkTicket[0];
            List<WorkTicket> result = new ArrayList<>(count);
            while (result.size() < count) {
                MarketEconomyState state = pollNextLocked();
                if (state == null) break;
                if (state.isQuarantined()) {
                    state.setQueued(false);
                    state.setUrgentQueued(false);
                    continue;
                }
                state.setQueued(false);
                state.setUrgentQueued(false);

                int capturedMask = state.getDirtyMask();
                int capturedPriority = state.getPriorityHint();
                long capturedSince = state.getFirstDirtyNanos();
                long capturedDirtyGeneration = state.getDirtyGeneration();

                state.setDirtyMask(0);
                state.setPriorityHint(PRIORITY_NORMAL);
                state.setFirstDirtyNanos(0L);
                state.setLastDirtyNanos(0L);
                state.setSnapshotBuilding(true);
                claimedTickets++;
                result.add(new WorkTicket(
                        state.getMarketId(), state.getMarket(),
                        state.getDeliveredGeneration(), state.getStructuralGeneration(),
                        capturedDirtyGeneration, capturedMask,
                        capturedPriority, capturedSince));
            }
            return result.toArray(new WorkTicket[0]);
        }
    }

    public static boolean markWorkRunning(WorkTicket ticket) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket) || !state.isSnapshotBuilding()) {
                lifecycleRejects++;
                return false;
            }
            state.setSnapshotBuilding(false);
            state.setRunning(true);
            return true;
        }
    }

    public static boolean markResultReady(WorkTicket ticket) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket) || !state.isRunning()) {
                lifecycleRejects++;
                return false;
            }
            state.setRunning(false);
            state.setResultReady(true);
            return true;
        }
    }

    public static boolean isCurrent(WorkTicket ticket) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            return matchesGenerationsLocked(STATES_BY_ID.get(ticket.marketId), ticket);
        }
    }

    /** Commits only live condition/industry materialization for a market. */
    public static boolean commitMaterializedState(Object market, long computeNanos) {
        int mask = SchedulerBridge.DIRTY_STRUCTURE
                | SchedulerBridge.DIRTY_INDUSTRIES
                | SchedulerBridge.DIRTY_CONDITIONS
                | DIRTY_TIME_DELIVERED
                | DIRTY_VALUE_STATE
                | DIRTY_INITIAL_REGISTRATION;
        return commitSynchronousMask(market, mask, computeNanos);
    }

    /** Commits only the authoritative local trade-snapshot publication. */
    public static boolean commitTradeSnapshot(Object market, long computeNanos) {
        int mask = SchedulerBridge.DIRTY_DERIVED_ECONOMY
                | DIRTY_ACCESSIBILITY | DIRTY_TRADE | DIRTY_GLOBAL_REVISION;
        return commitSynchronousMask(market, mask, computeNanos);
    }

    private static boolean commitSynchronousMask(
            Object market, int completedMask, long computeNanos) {
        if (market == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) return false;
            if (state.isSnapshotBuilding() || state.isRunning() || state.isResultReady()) {
                lifecycleRejects++;
                return false;
            }
            removeQueuedLocked(state);
            state.setDirtyMask(state.getDirtyMask() & ~completedMask);
            state.setLastComputeNanos(Math.max(0L, computeNanos));
            if (state.getDirtyMask() == 0) {
                state.setDerivedGeneration(Math.max(
                        state.getDerivedGeneration(), state.getDirtyGeneration()));
                state.setPriorityHint(PRIORITY_NORMAL);
                state.setFirstDirtyNanos(0L);
                state.setLastDirtyNanos(0L);
            }
            committedTickets++;
            enqueueIfIdleLocked(state);
            return true;
        }
    }

    public static boolean commitDerived(WorkTicket ticket, long computeNanos) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return false;
            if (!state.isResultReady()) {
                lifecycleRejects++;
                return false;
            }

            boolean current = matchesGenerationsLocked(state, ticket);
            state.setResultReady(false);
            if (current) {
                state.setDerivedGeneration(Math.max(
                        state.getDerivedGeneration(), ticket.dirtyGeneration));
                state.setLastComputeNanos(Math.max(0L, computeNanos));
                committedTickets++;
            } else {
                staleTickets++;
                restoreTicketDirtyLocked(state, ticket);
            }
            enqueueIfIdleLocked(state);
            return current;
        }
    }

    public static void abandon(WorkTicket ticket, boolean restoreCapturedDirty) {
        if (ticket == null) return;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return;
            state.setSnapshotBuilding(false);
            state.setRunning(false);
            state.setResultReady(false);
            if (restoreCapturedDirty) restoreTicketDirtyLocked(state, ticket);
            enqueueIfIdleLocked(state);
        }
    }

    /** Records a deterministic capture/model failure. Two identical failures quarantine the generation. */
    public static void recordFailure(WorkTicket ticket, String reason) {
        if (ticket == null) return;
        synchronized (LOCK) {
            MarketEconomyState state = STATES_BY_ID.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return;
            state.setSnapshotBuilding(false);
            state.setRunning(false);
            state.setResultReady(false);
            restoreTicketDirtyLocked(state, ticket);
            if (state.getFailureGeneration() == ticket.dirtyGeneration) {
                state.setFailureCount(state.getFailureCount() + 1);
            } else {
                state.setFailureGeneration(ticket.dirtyGeneration);
                state.setFailureCount(1);
            }
            state.setLastFailure(reason == null ? "unknown" : reason);
            if (state.getFailureCount() >= 2) {
                state.setQuarantined(true);
                removeQueuedLocked(state);
            } else {
                enqueueIfIdleLocked(state);
            }
        }
    }

    public static void quarantineMarket(Object market, String reason) {
        if (market == null) return;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) return;
            state.setQuarantined(true);
            state.setLastFailure(reason == null ? "unknown" : reason);
            state.setFailureGeneration(state.getDirtyGeneration());
            state.setFailureCount(Math.max(2, state.getFailureCount()));
            removeQueuedLocked(state);
        }
    }

    public static boolean isQuarantined(Object market) {
        if (market == null) return false;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state != null && state.isQuarantined();
        }
    }

    public static List<StateSnapshot> snapshotStates() {
        synchronized (LOCK) {
            List<StateSnapshot> result = new ArrayList<>(STATES_BY_ID.size());
            for (MarketEconomyState state : STATES_BY_ID.values()) {
                result.add(new StateSnapshot(state));
            }
            return Collections.unmodifiableList(result);
        }
    }

    /**
     * Cold recovery used only after a hard global delivery barrier. It repairs
     * missed loader-neutral callbacks without putting an O(n) scan in normal
     * price or frame paths.
     */
    public static int resynchronizeRuntimeGenerations() {
        int repaired = 0;
        synchronized (LOCK) {
            for (MarketEconomyState state : STATES_BY_ID.values()) {
                Object market = state.getMarket();
                if (market == null) continue;
                long delivered = SchedulerBridge.getDeliveredMarketGeneration(market);
                long structural = SchedulerBridge.getMarketStructuralGeneration(market);
                int dirty = 0;
                if (delivered > state.getDeliveredGeneration()) {
                    state.setDeliveredGeneration(delivered);
                    dirty |= DIRTY_TIME_DELIVERED | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
                }
                if (structural > state.getStructuralGeneration()) {
                    state.setStructuralGeneration(structural);
                    dirty |= SchedulerBridge.DIRTY_STRUCTURE
                            | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
                }
                if (dirty != 0) {
                    markDirtyLocked(state, dirty, PRIORITY_IMMEDIATE);
                    repaired++;
                }
            }
        }
        return repaired;
    }

    public static String statusSummary() {
        synchronized (LOCK) {
            int busy = 0;
            int quarantined = 0;
            for (MarketEconomyState state : STATES_BY_ID.values()) {
                if (state.isSnapshotBuilding() || state.isRunning() || state.isResultReady()) busy++;
                if (state.isQuarantined()) quarantined++;
            }
            return "markets=" + STATES_BY_ID.size()
                    + ", queued=" + (URGENT.size() + NORMAL.size())
                    + ", urgent=" + URGENT.size()
                    + ", busy=" + busy
                    + ", quarantined=" + quarantined
                    + ", registryGeneration=" + registryGeneration
                    + ", dirtyGeneration=" + dirtyGeneration
                    + ", claimed=" + claimedTickets
                    + ", committed=" + committedTickets
                    + ", stale=" + staleTickets
                    + ", coalesced=" + coalescedEvents
                    + ", duplicateDelivery=" + duplicateDeliveryEvents
                    + ", replacedIdentity=" + replacedMarketIdentities
                    + ", lifecycleRejects=" + lifecycleRejects
                    + ", priceClaims=" + priceClaims
                    + ", priceCommits=" + priceCommits
                    + ", stalePrice=" + stalePriceResults
                    + ", unknownDelivery=" + unknownDeliveryEvents
                    + ", unknownMutation=" + unknownMutationEvents;
        }
    }

    public static int size() { synchronized (LOCK) { return STATES_BY_ID.size(); } }
    public static int queuedCount() { synchronized (LOCK) { return URGENT.size() + NORMAL.size(); } }
    public static long getRegistryGeneration() { synchronized (LOCK) { return registryGeneration; } }
    public static long getDirtyGeneration() { synchronized (LOCK) { return dirtyGeneration; } }

    private static MarketEconomyState stateForMarketLocked(Object market) {
        String id = IDS_BY_MARKET.get(market);
        return id == null ? null : STATES_BY_ID.get(id);
    }

    private static void markDirtyLocked(
            MarketEconomyState state, int dirtyMask, int priorityHint) {
        if (dirtyMask == 0) return;
        long now = System.nanoTime();
        if (state.getDirtyMask() != 0 || state.isQueued()
                || state.isSnapshotBuilding() || state.isRunning() || state.isResultReady()) {
            coalescedEvents++;
        }
        if (state.getFirstDirtyNanos() == 0L) state.setFirstDirtyNanos(now);
        state.setLastDirtyNanos(now);
        state.setDirtyMask(state.getDirtyMask() | dirtyMask);
        state.setDirtyGeneration(nextPositive(dirtyGeneration));
        dirtyGeneration = state.getDirtyGeneration();
        state.setQuarantined(false);
        state.setFailureCount(0);
        state.setFailureGeneration(0L);
        state.setLastFailure(null);
        if (priorityHint > state.getPriorityHint()) state.setPriorityHint(priorityHint);
        enqueueIfIdleLocked(state);
    }

    private static void restoreTicketDirtyLocked(
            MarketEconomyState state, WorkTicket ticket) {
        state.setDirtyMask(state.getDirtyMask() | ticket.dirtyMask);
        if (ticket.priorityHint > state.getPriorityHint()) {
            state.setPriorityHint(ticket.priorityHint);
        }
        if (ticket.dirtySinceNanos > 0L
                && (state.getFirstDirtyNanos() == 0L
                || ticket.dirtySinceNanos < state.getFirstDirtyNanos())) {
            state.setFirstDirtyNanos(ticket.dirtySinceNanos);
        }
        if (state.getLastDirtyNanos() == 0L) state.setLastDirtyNanos(System.nanoTime());
    }

    private static void enqueueIfIdleLocked(MarketEconomyState state) {
        if (state == null || state.isQuarantined() || state.getDirtyMask() == 0 || state.isQueued()
                || state.isSnapshotBuilding() || state.isRunning() || state.isResultReady()) return;
        state.setQueued(true);
        if (state.getPriorityHint() > PRIORITY_NORMAL) {
            state.setUrgentQueued(true);
            URGENT.addLast(state);
        } else {
            state.setUrgentQueued(false);
            NORMAL.addLast(state);
        }
    }

    private static MarketEconomyState pollNextLocked() {
        MarketEconomyState state;
        if (!URGENT.isEmpty() && (NORMAL.isEmpty() || urgentBurst < 7)) {
            urgentBurst++;
            state = URGENT.pollFirst();
        } else {
            urgentBurst = 0;
            state = NORMAL.pollFirst();
            if (state == null) state = URGENT.pollFirst();
        }
        return state;
    }

    private static void removeQueuedLocked(MarketEconomyState state) {
        if (state == null || !state.isQueued()) return;
        if (state.isUrgentQueued()) URGENT.remove(state); else NORMAL.remove(state);
        state.setQueued(false);
        state.setUrgentQueued(false);
    }

    private static boolean matchesIdentityLocked(MarketEconomyState state, WorkTicket ticket) {
        return state != null && state.getMarket() == ticket.market;
    }

    private static boolean matchesGenerationsLocked(MarketEconomyState state, WorkTicket ticket) {
        return matchesIdentityLocked(state, ticket)
                && state.getDeliveredGeneration() == ticket.deliveredGeneration
                && state.getStructuralGeneration() == ticket.structuralGeneration
                && state.getDirtyGeneration() == ticket.dirtyGeneration;
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    public static final class WorkTicket {
        public final String marketId;
        public final Object market;
        public final long deliveredGeneration;
        public final long structuralGeneration;
        public final long dirtyGeneration;
        public final int dirtyMask;
        public final int priorityHint;
        public final long dirtySinceNanos;

        WorkTicket(String marketId, Object market, long deliveredGeneration,
                   long structuralGeneration, long dirtyGeneration, int dirtyMask,
                   int priorityHint, long dirtySinceNanos) {
            this.marketId = marketId;
            this.market = market;
            this.deliveredGeneration = deliveredGeneration;
            this.structuralGeneration = structuralGeneration;
            this.dirtyGeneration = dirtyGeneration;
            this.dirtyMask = dirtyMask;
            this.priorityHint = priorityHint;
            this.dirtySinceNanos = dirtySinceNanos;
        }
    }

    public static final class StateSnapshot {
        public final String marketId;
        public final long deliveredGeneration;
        public final long structuralGeneration;
        public final long derivedGeneration;
        public final long priceGeneration;
        public final long dirtyGeneration;
        public final int dirtyMask;
        public final int priorityHint;
        public final boolean queued;
        public final boolean busy;
        public final long dirtyAgeNanos;
        public final long lastComputeNanos;

        StateSnapshot(MarketEconomyState state) {
            marketId = state.getMarketId();
            deliveredGeneration = state.getDeliveredGeneration();
            structuralGeneration = state.getStructuralGeneration();
            derivedGeneration = state.getDerivedGeneration();
            priceGeneration = state.getPriceGeneration();
            dirtyGeneration = state.getDirtyGeneration();
            dirtyMask = state.getDirtyMask();
            priorityHint = state.getPriorityHint();
            queued = state.isQueued();
            busy = state.isSnapshotBuilding() || state.isRunning() || state.isResultReady();
            dirtyAgeNanos = state.getFirstDirtyNanos() == 0L
                    ? 0L : Math.max(0L, System.nanoTime() - state.getFirstDirtyNanos());
            lastComputeNanos = state.getLastComputeNanos();
        }
    }
}
