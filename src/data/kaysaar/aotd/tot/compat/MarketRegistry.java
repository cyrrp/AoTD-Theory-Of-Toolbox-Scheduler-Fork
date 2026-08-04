package data.kaysaar.aotd.tot.compat;

import data.kaysaar.aotd.tot.scripts.economy.AoTDRuntimeEpoch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AoTD-owned market registry and coalescing dirty queue.
 *
 * <p>The dirty mask is a work queue, not a causal validity token. Each market also owns a
 * domain-specific revision vector. Price results are validated only against price dependencies;
 * trade/accessibility-only mutations therefore do not invalidate an in-flight price result.
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

    /** Bits fully satisfied by the local price/stockpile commit. */
    public static final int PRICE_WORK_MASK = DIRTY_PRICE | DIRTY_STOCKPILE;

    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_VISIBLE = 10;
    public static final int PRIORITY_PLAYER = 20;
    public static final int PRIORITY_IMMEDIATE = 30;

    private static final int MATERIALIZED_WORK_MASK =
            SchedulerBridge.DIRTY_STRUCTURE
                    | SchedulerBridge.DIRTY_INDUSTRIES
                    | SchedulerBridge.DIRTY_CONDITIONS
                    | DIRTY_TIME_DELIVERED
                    | DIRTY_VALUE_STATE
                    | DIRTY_INITIAL_REGISTRATION;

    private static final int TRADE_WORK_MASK =
            SchedulerBridge.DIRTY_STRUCTURE
                    | SchedulerBridge.DIRTY_INDUSTRIES
                    | SchedulerBridge.DIRTY_CONDITIONS
                    | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                    | DIRTY_TIME_DELIVERED
                    | DIRTY_VALUE_STATE
                    | DIRTY_ACCESSIBILITY
                    | DIRTY_TRADE
                    | DIRTY_GLOBAL_REVISION
                    | DIRTY_INITIAL_REGISTRATION;

    private static final int PRICE_REVISION_MASK =
            SchedulerBridge.DIRTY_STRUCTURE
                    | SchedulerBridge.DIRTY_INDUSTRIES
                    | SchedulerBridge.DIRTY_CONDITIONS
                    | DIRTY_VALUE_STATE
                    | DIRTY_PRICE
                    | DIRTY_INITIAL_REGISTRATION;

    private static final int MATERIALIZED_REVISION_MASK =
            SchedulerBridge.DIRTY_STRUCTURE
                    | SchedulerBridge.DIRTY_INDUSTRIES
                    | SchedulerBridge.DIRTY_CONDITIONS
                    | DIRTY_VALUE_STATE
                    | DIRTY_INITIAL_REGISTRATION;

    private static final int ACCESSIBILITY_REVISION_MASK =
            SchedulerBridge.DIRTY_STRUCTURE
                    | SchedulerBridge.DIRTY_CONDITIONS
                    | DIRTY_ACCESSIBILITY
                    | DIRTY_GLOBAL_REVISION
                    | DIRTY_INITIAL_REGISTRATION;

    private static final int TRADE_REVISION_MASK = TRADE_WORK_MASK;

    private static final int DEP_STRUCTURE = 1;
    private static final int DEP_MATERIALIZED = 1 << 1;
    private static final int DEP_PRICE_INPUT = 1 << 2;
    private static final int DEP_STOCKPILE = 1 << 3;
    private static final int DEP_ACCESSIBILITY = 1 << 4;
    private static final int DEP_TRADE_INPUT = 1 << 5;
    private static final int DEP_TEMPORAL = 1 << 6;
    private static final int PRICE_DEPENDENCIES =
            DEP_STRUCTURE | DEP_MATERIALIZED | DEP_PRICE_INPUT | DEP_STOCKPILE | DEP_TEMPORAL;

    private static final Object LOCK = new Object();

    /** Only this map is read without LOCK; full rebuild swaps the reference once. */
    private static volatile ConcurrentHashMap<String, Object> marketsById =
            new ConcurrentHashMap<>();

    private static Map<String, MarketEconomyState> statesById = new HashMap<>();
    private static IdentityHashMap<Object, String> idsByMarket = new IdentityHashMap<>();
    private static ArrayDeque<MarketEconomyState> urgent = new ArrayDeque<>();
    private static ArrayDeque<MarketEconomyState> normal = new ArrayDeque<>();
    private static volatile RegistryLifecycle registryLifecycle = RegistryLifecycle.EMPTY;

    private static long registryGeneration;

    /** Global event sequence retained for ordering/diagnostics only. */
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
    private static long synchronousCommitCommitted;
    private static long synchronousCommitUnknownMarket;
    private static long synchronousCommitSnapshotBuilding;
    private static long synchronousCommitRunning;
    private static long synchronousCommitResultReady;
    private static long invariantAudits;
    private static long invariantAuditFailures;
    private static long invariantViolations;
    private static long atomicPublications;
    private static long atomicPublicationFailures;
    private static final AtomicLong lookupsDuringBuild = new AtomicLong();
    private static long lookupMisses;
    private static long targetedRepairAttempts;
    private static long targetedRepairSuccesses;
    private static long negativeLookupHits;
    private static long fullRebuilds;
    private static long unrelatedPriceInvalidationsAvoided;
    private static long staleEpochTickets;
    private static long epochSafeDrops;
    private static int urgentBurst;

    private MarketRegistry() {}

    /** Clears loader-local runtime state when a new economy/save is installed. */
    public static void clear() {
        synchronized (LOCK) {
            marketsById = new ConcurrentHashMap<>();
            statesById = new HashMap<>();
            idsByMarket = new IdentityHashMap<>();
            urgent = new ArrayDeque<>();
            normal = new ArrayDeque<>();
            registryLifecycle = RegistryLifecycle.EMPTY;
            registryGeneration = nextPositive(registryGeneration);
            dirtyGeneration = 0L;
            resetCountersLocked();
        }
    }

    private static void resetCountersLocked() {
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
        synchronousCommitCommitted = 0L;
        synchronousCommitUnknownMarket = 0L;
        synchronousCommitSnapshotBuilding = 0L;
        synchronousCommitRunning = 0L;
        synchronousCommitResultReady = 0L;
        invariantAudits = 0L;
        invariantAuditFailures = 0L;
        invariantViolations = 0L;
        atomicPublications = 0L;
        atomicPublicationFailures = 0L;
        lookupsDuringBuild.set(0L);
        lookupMisses = 0L;
        targetedRepairAttempts = 0L;
        targetedRepairSuccesses = 0L;
        negativeLookupHits = 0L;
        fullRebuilds = 0L;
        unrelatedPriceInvalidationsAvoided = 0L;
        staleEpochTickets = 0L;
        epochSafeDrops = 0L;
        urgentBurst = 0;
    }

    /**
     * Builds all registry/state/queue structures while the previous complete lookup snapshot
     * remains visible, then publishes the new lookup map once.
     */
    public static int replaceAllMarkets(Map<String, ?> completeMarkets) {
        Map<String, ?> source = completeMarkets == null ? Collections.emptyMap() : completeMarkets;
        synchronized (LOCK) {
            RegistryLifecycle previousLifecycle = registryLifecycle;
            registryLifecycle = RegistryLifecycle.BUILDING;
            try {
                ConcurrentHashMap<String, Object> nextMarkets = new ConcurrentHashMap<>();
                Map<String, MarketEconomyState> nextStates = new HashMap<>();
                IdentityHashMap<Object, String> nextIdentities = new IdentityHashMap<>();
                ArrayDeque<MarketEconomyState> nextNormal = new ArrayDeque<>();
                ArrayDeque<MarketEconomyState> nextUrgent = new ArrayDeque<>();

                for (Map.Entry<String, ?> entry : source.entrySet()) {
                    String marketId = entry.getKey();
                    Object market = entry.getValue();
                    if (marketId == null || marketId.isEmpty() || market == null) continue;
                    if (nextMarkets.putIfAbsent(marketId, market) != null) {
                        throw new IllegalArgumentException("duplicate market id: " + marketId);
                    }
                    String previousId = nextIdentities.put(market, marketId);
                    if (previousId != null) {
                        throw new IllegalArgumentException(
                                "market identity appears under multiple ids: "
                                        + previousId
                                        + ", "
                                        + marketId);
                    }
                    MarketEconomyState state = new MarketEconomyState(marketId, market);
                    state.setDeliveredGeneration(
                            SchedulerBridge.getDeliveredMarketGeneration(market));
                    state.setStructuralGeneration(
                            SchedulerBridge.getMarketStructuralGeneration(market));
                    initializeDirtyStateLocked(
                            state,
                            SchedulerBridge.DIRTY_STRUCTURE
                                    | SchedulerBridge.DIRTY_INDUSTRIES
                                    | SchedulerBridge.DIRTY_CONDITIONS
                                    | SchedulerBridge.DIRTY_DERIVED_ECONOMY
                                    | DIRTY_INITIAL_REGISTRATION,
                            PRIORITY_NORMAL,
                            nextUrgent,
                            nextNormal);
                    nextStates.put(marketId, state);
                }

                statesById = nextStates;
                idsByMarket = nextIdentities;
                urgent = nextUrgent;
                normal = nextNormal;
                urgentBurst = 0;
                registryGeneration = nextPositive(registryGeneration);
                fullRebuilds++;
                atomicPublications++;
                marketsById = nextMarkets; // single lock-free publication point
                registryLifecycle =
                        nextMarkets.isEmpty() ? RegistryLifecycle.EMPTY : RegistryLifecycle.READY;
                return nextMarkets.size();
            } catch (RuntimeException | Error failure) {
                atomicPublicationFailures++;
                registryLifecycle = previousLifecycle;
                throw failure;
            }
        }
    }

    public static int replaceAllMarkets(Iterable<? extends Map.Entry<String, ?>> entries) {
        LinkedHashMap<String, Object> materialized = new LinkedHashMap<>();
        if (entries != null) {
            for (Map.Entry<String, ?> entry : entries) {
                if (entry != null) materialized.put(entry.getKey(), entry.getValue());
            }
        }
        return replaceAllMarkets(materialized);
    }

    public static void registerMarket(String marketId, Object market) {
        if (marketId == null || marketId.isEmpty() || market == null) return;
        synchronized (LOCK) {
            ConcurrentHashMap<String, Object> lookup = marketsById;
            Object previous = lookup.put(marketId, market);
            if (previous != null && previous != market) {
                idsByMarket.remove(previous);
                replacedMarketIdentities++;
            }

            String previousId = idsByMarket.put(market, marketId);
            if (previousId != null && !previousId.equals(marketId)) {
                lookup.remove(previousId, market);
                MarketEconomyState displaced = statesById.remove(previousId);
                removeQueuedLocked(displaced);
            }

            MarketEconomyState state = statesById.get(marketId);
            boolean created = state == null || state.getMarket() != market;
            if (created) {
                removeQueuedLocked(state);
                state = new MarketEconomyState(marketId, market);
                statesById.put(marketId, state);
            } else {
                state.setMarket(market);
            }

            long delivered = SchedulerBridge.getDeliveredMarketGeneration(market);
            long structural = SchedulerBridge.getMarketStructuralGeneration(market);
            if (delivered > state.getDeliveredGeneration()) state.setDeliveredGeneration(delivered);
            if (structural > state.getStructuralGeneration())
                state.setStructuralGeneration(structural);
            registryGeneration = nextPositive(registryGeneration);
            registryLifecycle = RegistryLifecycle.READY;

            if (created) {
                markDirtyLocked(
                        state,
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
                resolved = idsByMarket.get(market);
            }
            if (resolved == null) return;

            Object mapped = marketsById.get(resolved);
            if (market != null && mapped != null && mapped != market) {
                idsByMarket.remove(market);
                return;
            }

            Object removed = marketsById.remove(resolved);
            if (removed != null) idsByMarket.remove(removed);
            if (market != null) idsByMarket.remove(market);
            MarketEconomyState state = statesById.remove(resolved);
            removeQueuedLocked(state);
            registryGeneration = nextPositive(registryGeneration);
            if (marketsById.isEmpty()) registryLifecycle = RegistryLifecycle.EMPTY;
        }
    }

    /** Hot synchronous lookup: no registry monitor is acquired. */
    public static Object lookupMarket(String marketId) {
        if (marketId == null) return null;
        if (registryLifecycle == RegistryLifecycle.BUILDING) {
            lookupsDuringBuild.incrementAndGet();
        }
        return marketsById.get(marketId);
    }

    public static MarketEconomyState stateForId(String marketId) {
        synchronized (LOCK) {
            return statesById.get(marketId);
        }
    }

    public static RegistryLifecycle getRegistryLifecycle() {
        return registryLifecycle;
    }

    public static int getRegisteredMarketCount() {
        return marketsById.size();
    }

    public static void recordLookupMiss() {
        synchronized (LOCK) {
            lookupMisses++;
        }
    }

    public static void recordTargetedRepairAttempt() {
        synchronized (LOCK) {
            targetedRepairAttempts++;
        }
    }

    public static void recordTargetedRepairSuccess() {
        synchronized (LOCK) {
            targetedRepairSuccesses++;
        }
    }

    public static void recordNegativeLookupHit() {
        synchronized (LOCK) {
            negativeLookupHits++;
        }
    }

    public static void onMarketTimeDelivered(
            Object market, long deliveredGeneration, long deliverySequence, float deliveredAmount) {
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

            markDirtyLocked(
                    state,
                    DIRTY_TIME_DELIVERED | SchedulerBridge.DIRTY_DERIVED_ECONOMY,
                    PRIORITY_NORMAL);
        }
    }

    public static void onMarketMutationCommitted(
            Object market, int dirtyMask, long sourceGeneration, long structuralGeneration) {
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

    public static void markDirtyById(String marketId, int dirtyMask, int priorityHint) {
        if (marketId == null || dirtyMask == 0) return;
        synchronized (LOCK) {
            MarketEconomyState state = statesById.get(marketId);
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

    /** True while any output domain still needs a commit. */
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
                    || needsMaterializedVectorLocked(state)
                    || needsPriceVectorLocked(state)
                    || needsTradeVectorLocked(state);
        }
    }

    public static boolean needsMaterializedReconciliation(Object market) {
        if (market == null) return true;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state == null
                    || (state.getDirtyMask() & MATERIALIZED_WORK_MASK) != 0
                    || needsMaterializedVectorLocked(state);
        }
    }

    public static boolean needsTradeSnapshot(Object market) {
        if (market == null) return true;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            return state == null
                    || (state.getDirtyMask() & TRADE_WORK_MASK) != 0
                    || needsTradeVectorLocked(state);
        }
    }

    public static boolean needsPriceRefresh(Object market) {
        if (market == null) return true;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) return true;
            return (state.getDirtyMask() & PRICE_WORK_MASK) != 0 || needsPriceVectorLocked(state);
        }
    }

    public static WorkTicket claimMarketForPrice(Object market) {
        return claimMarketForPrice(market, AoTDRuntimeEpoch.captureBatch("market-price-ticket"));
    }

    public static WorkTicket claimMarketForPrice(Object market, AoTDRuntimeEpoch.Stamp stamp) {
        if (market == null || !AoTDRuntimeEpoch.isCurrent(stamp)) return null;
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null
                    || state.isQuarantined()
                    || state.isSnapshotBuilding()
                    || state.isRunning()
                    || state.isResultReady()) {
                return null;
            }
            if ((state.getDirtyMask() & PRICE_WORK_MASK) == 0 && !needsPriceVectorLocked(state)) {
                return null;
            }
            removeQueuedLocked(state);
            WorkTicket ticket =
                    captureTicketLocked(state, TicketKind.PRICE, PRICE_DEPENDENCIES, stamp);
            state.setDirtyMask(0);
            state.setPriorityHint(PRIORITY_NORMAL);
            state.setFirstDirtyNanos(0L);
            state.setLastDirtyNanos(0L);
            state.setSnapshotBuilding(true);
            claimedTickets++;
            priceClaims++;
            return ticket;
        }
    }

    public static boolean commitPriceDerived(WorkTicket ticket, long computeNanos) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return false;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return false;
            if (!state.isResultReady()) {
                lifecycleRejects++;
                return false;
            }
            boolean current = matchesGenerationsLocked(state, ticket);
            state.setResultReady(false);
            if (current) {
                state.commitPriceVector();
                state.setPriceGeneration(
                        Math.max(state.getPriceGeneration(), ticket.dirtyGeneration));
                state.setLastComputeNanos(Math.max(0L, computeNanos));
                int remaining = ticket.dirtyMask & ~PRICE_WORK_MASK;
                restoreMaskMetadataLocked(state, ticket, remaining);
                if (remaining == 0 && state.getDirtyMask() == 0) {
                    state.setDerivedGeneration(
                            Math.max(state.getDerivedGeneration(), ticket.dirtyGeneration));
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

    public static WorkTicket[] claimDirtyBatch(int maxMarkets) {
        AoTDRuntimeEpoch.Stamp stamp = AoTDRuntimeEpoch.captureBatch("market-dirty-batch");
        if (maxMarkets <= 0 || !AoTDRuntimeEpoch.isCurrent(stamp)) {
            return new WorkTicket[0];
        }
        synchronized (LOCK) {
            int count = Math.min(maxMarkets, urgent.size() + normal.size());
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
                int dependencies = dependenciesForDirtyMask(state.getDirtyMask());
                WorkTicket ticket =
                        captureTicketLocked(state, TicketKind.GENERAL, dependencies, stamp);
                state.setDirtyMask(0);
                state.setPriorityHint(PRIORITY_NORMAL);
                state.setFirstDirtyNanos(0L);
                state.setLastDirtyNanos(0L);
                state.setSnapshotBuilding(true);
                claimedTickets++;
                result.add(ticket);
            }
            return result.toArray(new WorkTicket[0]);
        }
    }

    public static boolean markWorkRunning(WorkTicket ticket) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return false;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
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
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return false;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
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
            if (!matchesEpochLocked(ticket)) return false;
            return matchesGenerationsLocked(statesById.get(ticket.marketId), ticket);
        }
    }

    public static boolean commitMaterializedState(Object market, long computeNanos) {
        return commitMaterializedStateDetailed(market, computeNanos) == CommitStatus.COMMITTED;
    }

    public static CommitStatus commitMaterializedStateDetailed(Object market, long computeNanos) {
        return commitSynchronousMask(
                market, MATERIALIZED_WORK_MASK, OutputDomain.MATERIALIZED, computeNanos);
    }

    public static boolean commitTradeSnapshot(Object market, long computeNanos) {
        return commitTradeSnapshotDetailed(market, computeNanos) == CommitStatus.COMMITTED;
    }

    public static CommitStatus commitTradeSnapshotDetailed(Object market, long computeNanos) {
        return commitSynchronousMask(
                market,
                SchedulerBridge.DIRTY_DERIVED_ECONOMY
                        | DIRTY_ACCESSIBILITY
                        | DIRTY_TRADE
                        | DIRTY_GLOBAL_REVISION,
                OutputDomain.TRADE,
                computeNanos);
    }

    private static CommitStatus commitSynchronousMask(
            Object market, int completedMask, OutputDomain domain, long computeNanos) {
        if (market == null) {
            synchronized (LOCK) {
                synchronousCommitUnknownMarket++;
            }
            return CommitStatus.UNKNOWN_MARKET;
        }
        synchronized (LOCK) {
            MarketEconomyState state = stateForMarketLocked(market);
            if (state == null) {
                synchronousCommitUnknownMarket++;
                return CommitStatus.UNKNOWN_MARKET;
            }
            if (state.isSnapshotBuilding()) {
                lifecycleRejects++;
                synchronousCommitSnapshotBuilding++;
                return CommitStatus.SNAPSHOT_BUILDING;
            }
            if (state.isRunning()) {
                lifecycleRejects++;
                synchronousCommitRunning++;
                return CommitStatus.RUNNING;
            }
            if (state.isResultReady()) {
                lifecycleRejects++;
                synchronousCommitResultReady++;
                return CommitStatus.RESULT_READY;
            }
            removeQueuedLocked(state);
            state.setDirtyMask(state.getDirtyMask() & ~completedMask);
            state.setLastComputeNanos(Math.max(0L, computeNanos));
            if (domain == OutputDomain.MATERIALIZED) state.commitMaterializedVector();
            if (domain == OutputDomain.TRADE) state.commitTradeVector();
            if (state.getDirtyMask() == 0) {
                state.setDerivedGeneration(
                        Math.max(state.getDerivedGeneration(), state.getDirtyGeneration()));
                state.setPriorityHint(PRIORITY_NORMAL);
                state.setFirstDirtyNanos(0L);
                state.setLastDirtyNanos(0L);
            }
            committedTickets++;
            synchronousCommitCommitted++;
            enqueueIfIdleLocked(state);
            return CommitStatus.COMMITTED;
        }
    }

    public static boolean commitDerived(WorkTicket ticket, long computeNanos) {
        if (ticket == null) return false;
        synchronized (LOCK) {
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return false;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return false;
            if (!state.isResultReady()) {
                lifecycleRejects++;
                return false;
            }
            boolean current = matchesGenerationsLocked(state, ticket);
            state.setResultReady(false);
            if (current) {
                commitVectorsForMaskLocked(state, ticket.dirtyMask);
                state.setDerivedGeneration(
                        Math.max(state.getDerivedGeneration(), ticket.dirtyGeneration));
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
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return;
            state.setSnapshotBuilding(false);
            state.setRunning(false);
            state.setResultReady(false);
            if (restoreCapturedDirty) restoreTicketDirtyLocked(state, ticket);
            enqueueIfIdleLocked(state);
        }
    }

    public static void recordFailure(WorkTicket ticket, String reason) {
        if (ticket == null) return;
        synchronized (LOCK) {
            if (!matchesEpochLocked(ticket)) {
                staleEpochTickets++;
                epochSafeDrops++;
                return;
            }
            MarketEconomyState state = statesById.get(ticket.marketId);
            if (!matchesIdentityLocked(state, ticket)) return;
            state.setSnapshotBuilding(false);
            state.setRunning(false);
            state.setResultReady(false);
            restoreTicketDirtyLocked(state, ticket);
            long failureKey = ticket.validationFingerprint;
            if (state.getFailureGeneration() == failureKey) {
                state.setFailureCount(state.getFailureCount() + 1);
            } else {
                state.setFailureGeneration(failureKey);
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
            List<StateSnapshot> result = new ArrayList<>(statesById.size());
            for (MarketEconomyState state : statesById.values()) {
                result.add(new StateSnapshot(state));
            }
            return Collections.unmodifiableList(result);
        }
    }

    public static int resynchronizeRuntimeGenerations() {
        int repaired = 0;
        synchronized (LOCK) {
            for (MarketEconomyState state : statesById.values()) {
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
                    dirty |=
                            SchedulerBridge.DIRTY_STRUCTURE | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
                }
                if (dirty != 0) {
                    markDirtyLocked(state, dirty, PRIORITY_IMMEDIATE);
                    repaired++;
                }
            }
        }
        return repaired;
    }

    public static String describeCommitState(Object market) {
        synchronized (LOCK) {
            String marketId = market == null ? null : idsByMarket.get(market);
            MarketEconomyState state = marketId == null ? null : statesById.get(marketId);
            if (state == null) {
                return "marketId="
                        + marketId
                        + ", registered="
                        + (marketId != null)
                        + ", registryGeneration="
                        + registryGeneration
                        + ", registryMarkets="
                        + marketsById.size()
                        + ", registryStates="
                        + statesById.size()
                        + ", registryLifecycle="
                        + registryLifecycle;
            }
            return "marketId="
                    + state.getMarketId()
                    + ", dirtyMask=0x"
                    + Integer.toHexString(state.getDirtyMask())
                    + ", dirtyGeneration="
                    + state.getDirtyGeneration()
                    + ", revisions="
                    + RevisionVector.of(state)
                    + ", queued="
                    + state.isQueued()
                    + ", snapshotBuilding="
                    + state.isSnapshotBuilding()
                    + ", running="
                    + state.isRunning()
                    + ", resultReady="
                    + state.isResultReady()
                    + ", registryGeneration="
                    + registryGeneration
                    + ", registryLifecycle="
                    + registryLifecycle;
        }
    }

    public static InvariantReport auditInvariants(Map<String, ?> expectedMarkets) {
        synchronized (LOCK) {
            invariantAudits++;
            ArrayList<String> samples = new ArrayList<>();
            int violations = 0;
            final int sampleLimit = 20;

            if (marketsById.size() != statesById.size()) {
                violations =
                        addViolation(
                                samples,
                                sampleLimit,
                                violations,
                                "registry/state size mismatch: registry="
                                        + marketsById.size()
                                        + ", states="
                                        + statesById.size());
            }
            if (marketsById.size() != idsByMarket.size()) {
                violations =
                        addViolation(
                                samples,
                                sampleLimit,
                                violations,
                                "registry/identity size mismatch: registry="
                                        + marketsById.size()
                                        + ", identities="
                                        + idsByMarket.size());
            }

            IdentityHashMap<MarketEconomyState, Integer> queueMembership = new IdentityHashMap<>();
            for (MarketEconomyState state : urgent) {
                queueMembership.put(state, queueMembership.getOrDefault(state, 0) + 1);
                if (!state.isQueued() || !state.isUrgentQueued()) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "urgent queue flags invalid: " + state.getMarketId());
                }
            }
            for (MarketEconomyState state : normal) {
                queueMembership.put(state, queueMembership.getOrDefault(state, 0) + 1);
                if (!state.isQueued() || state.isUrgentQueued()) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "normal queue flags invalid: " + state.getMarketId());
                }
            }

            for (Map.Entry<String, Object> entry : marketsById.entrySet()) {
                String marketId = entry.getKey();
                Object market = entry.getValue();
                MarketEconomyState state = statesById.get(marketId);
                if (state == null || state.getMarket() != market) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "market/state identity mismatch: " + marketId);
                }
                if (!marketId.equals(idsByMarket.get(market))) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "reverse identity mismatch: " + marketId);
                }
            }

            for (MarketEconomyState state : statesById.values()) {
                int membership = queueMembership.getOrDefault(state, 0);
                if (membership > 1 || state.isQueued() != (membership == 1)) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "queue membership mismatch: "
                                            + state.getMarketId()
                                            + ", queued="
                                            + state.isQueued()
                                            + ", count="
                                            + membership);
                }
                int lifecycleStates =
                        (state.isQueued() ? 1 : 0)
                                + (state.isSnapshotBuilding() ? 1 : 0)
                                + (state.isRunning() ? 1 : 0)
                                + (state.isResultReady() ? 1 : 0);
                if (lifecycleStates > 1) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "overlapping lifecycle states: " + state.getMarketId());
                }
            }

            int expectedCount = expectedMarkets == null ? -1 : expectedMarkets.size();
            if (expectedMarkets != null) {
                if (expectedMarkets.size() != marketsById.size()) {
                    violations =
                            addViolation(
                                    samples,
                                    sampleLimit,
                                    violations,
                                    "economy/registry size mismatch: expected="
                                            + expectedMarkets.size()
                                            + ", registry="
                                            + marketsById.size());
                }
                for (Map.Entry<String, ?> entry : expectedMarkets.entrySet()) {
                    Object registered = marketsById.get(entry.getKey());
                    if (registered == null || registered != entry.getValue()) {
                        violations =
                                addViolation(
                                        samples,
                                        sampleLimit,
                                        violations,
                                        "economy/registry identity mismatch: " + entry.getKey());
                    }
                }
            }

            if (violations > 0) {
                invariantAuditFailures++;
                invariantViolations += violations;
            }
            return new InvariantReport(
                    expectedCount,
                    marketsById.size(),
                    statesById.size(),
                    idsByMarket.size(),
                    urgent.size() + normal.size(),
                    registryGeneration,
                    registryLifecycle,
                    violations,
                    samples);
        }
    }

    public static InvariantReport auditInvariants() {
        return auditInvariants(null);
    }

    private static int addViolation(
            List<String> samples, int sampleLimit, int current, String description) {
        if (samples.size() < sampleLimit) samples.add(description);
        return current + 1;
    }

    public static String statusSummary() {
        synchronized (LOCK) {
            int busy = 0;
            int quarantined = 0;
            for (MarketEconomyState state : statesById.values()) {
                if (state.isSnapshotBuilding() || state.isRunning() || state.isResultReady())
                    busy++;
                if (state.isQuarantined()) quarantined++;
            }
            return "markets="
                    + statesById.size()
                    + ", queued="
                    + (urgent.size() + normal.size())
                    + ", urgent="
                    + urgent.size()
                    + ", busy="
                    + busy
                    + ", quarantined="
                    + quarantined
                    + ", registryLifecycle="
                    + registryLifecycle
                    + ", registryGeneration="
                    + registryGeneration
                    + ", dirtyEventSequence="
                    + dirtyGeneration
                    + ", claimed="
                    + claimedTickets
                    + ", committed="
                    + committedTickets
                    + ", stale="
                    + staleTickets
                    + ", coalesced="
                    + coalescedEvents
                    + ", lifecycleRejects="
                    + lifecycleRejects
                    + ", priceClaims="
                    + priceClaims
                    + ", priceCommits="
                    + priceCommits
                    + ", stalePrice="
                    + stalePriceResults
                    + ", staleEpochTickets="
                    + staleEpochTickets
                    + ", epochSafeDrops="
                    + epochSafeDrops
                    + ", campaignEpoch="
                    + AoTDRuntimeEpoch.getCampaignEpoch()
                    + ", economyEpoch="
                    + AoTDRuntimeEpoch.getEconomyEpoch()
                    + ", avoidedUnrelatedPriceInvalidations="
                    + unrelatedPriceInvalidationsAvoided
                    + ", syncCommitCommitted="
                    + synchronousCommitCommitted
                    + ", syncCommitUnknownMarket="
                    + synchronousCommitUnknownMarket
                    + ", syncCommitSnapshotBuilding="
                    + synchronousCommitSnapshotBuilding
                    + ", syncCommitRunning="
                    + synchronousCommitRunning
                    + ", syncCommitResultReady="
                    + synchronousCommitResultReady
                    + ", atomicPublications="
                    + atomicPublications
                    + ", atomicPublicationFailures="
                    + atomicPublicationFailures
                    + ", lookupsDuringBuild="
                    + lookupsDuringBuild.get()
                    + ", lookupMisses="
                    + lookupMisses
                    + ", targetedRepairAttempts="
                    + targetedRepairAttempts
                    + ", targetedRepairSuccesses="
                    + targetedRepairSuccesses
                    + ", negativeLookupHits="
                    + negativeLookupHits
                    + ", fullRebuilds="
                    + fullRebuilds
                    + ", invariantAudits="
                    + invariantAudits
                    + ", invariantAuditFailures="
                    + invariantAuditFailures
                    + ", invariantViolations="
                    + invariantViolations
                    + ", duplicateDelivery="
                    + duplicateDeliveryEvents
                    + ", replacedIdentity="
                    + replacedMarketIdentities
                    + ", unknownDelivery="
                    + unknownDeliveryEvents
                    + ", unknownMutation="
                    + unknownMutationEvents;
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return statesById.size();
        }
    }

    public static int queuedCount() {
        synchronized (LOCK) {
            return urgent.size() + normal.size();
        }
    }

    public static long getRegistryGeneration() {
        synchronized (LOCK) {
            return registryGeneration;
        }
    }

    public static long getDirtyGeneration() {
        synchronized (LOCK) {
            return dirtyGeneration;
        }
    }

    public static long getAtomicPublicationCount() {
        synchronized (LOCK) {
            return atomicPublications;
        }
    }

    public static long getAtomicPublicationFailureCount() {
        synchronized (LOCK) {
            return atomicPublicationFailures;
        }
    }

    private static MarketEconomyState stateForMarketLocked(Object market) {
        String id = idsByMarket.get(market);
        return id == null ? null : statesById.get(id);
    }

    private static void initializeDirtyStateLocked(
            MarketEconomyState state,
            int dirtyMask,
            int priorityHint,
            ArrayDeque<MarketEconomyState> targetUrgent,
            ArrayDeque<MarketEconomyState> targetNormal) {
        long now = System.nanoTime();
        state.setFirstDirtyNanos(now);
        state.setLastDirtyNanos(now);
        state.setDirtyMask(dirtyMask);
        state.setDirtyGeneration(nextPositive(dirtyGeneration));
        dirtyGeneration = state.getDirtyGeneration();
        advanceDomainRevisionsLocked(state, dirtyMask);
        state.setPriorityHint(priorityHint);
        state.setQueued(true);
        if (priorityHint > PRIORITY_NORMAL) {
            state.setUrgentQueued(true);
            targetUrgent.addLast(state);
        } else {
            state.setUrgentQueued(false);
            targetNormal.addLast(state);
        }
    }

    private static void markDirtyLocked(MarketEconomyState state, int dirtyMask, int priorityHint) {
        if (dirtyMask == 0) return;
        long now = System.nanoTime();
        boolean priceWasCurrent =
                !needsPriceVectorLocked(state) && (state.getDirtyMask() & PRICE_WORK_MASK) == 0;
        if (state.getDirtyMask() != 0
                || state.isQueued()
                || state.isSnapshotBuilding()
                || state.isRunning()
                || state.isResultReady()) {
            coalescedEvents++;
        }
        if (state.getFirstDirtyNanos() == 0L) state.setFirstDirtyNanos(now);
        state.setLastDirtyNanos(now);
        state.setDirtyMask(state.getDirtyMask() | dirtyMask);
        state.setDirtyGeneration(nextPositive(dirtyGeneration));
        dirtyGeneration = state.getDirtyGeneration();
        advanceDomainRevisionsLocked(state, dirtyMask);
        if (priceWasCurrent
                && (dirtyMask & PRICE_REVISION_MASK) == 0
                && (dirtyMask & DIRTY_STOCKPILE) == 0
                && (dirtyMask & DIRTY_TIME_DELIVERED) == 0) {
            unrelatedPriceInvalidationsAvoided++;
        }
        state.setQuarantined(false);
        state.setFailureCount(0);
        state.setFailureGeneration(0L);
        state.setLastFailure(null);
        if (priorityHint > state.getPriorityHint()) state.setPriorityHint(priorityHint);
        enqueueIfIdleLocked(state);
    }

    private static void advanceDomainRevisionsLocked(MarketEconomyState state, int dirtyMask) {
        if ((dirtyMask & (SchedulerBridge.DIRTY_STRUCTURE | DIRTY_INITIAL_REGISTRATION)) != 0) {
            state.setStructureRevision(nextPositive(state.getStructureRevision()));
        }
        if ((dirtyMask & MATERIALIZED_REVISION_MASK) != 0) {
            state.setMaterializedRevision(nextPositive(state.getMaterializedRevision()));
        }
        if ((dirtyMask & PRICE_REVISION_MASK) != 0) {
            state.setPriceInputRevision(nextPositive(state.getPriceInputRevision()));
        }
        if ((dirtyMask
                        & (DIRTY_STOCKPILE
                                | SchedulerBridge.DIRTY_STRUCTURE
                                | DIRTY_INITIAL_REGISTRATION))
                != 0) {
            state.setStockpileRevision(nextPositive(state.getStockpileRevision()));
        }
        if ((dirtyMask & ACCESSIBILITY_REVISION_MASK) != 0) {
            state.setAccessibilityRevision(nextPositive(state.getAccessibilityRevision()));
        }
        if ((dirtyMask & TRADE_REVISION_MASK) != 0) {
            state.setTradeInputRevision(nextPositive(state.getTradeInputRevision()));
        }
        if ((dirtyMask & DIRTY_TIME_DELIVERED) != 0) {
            state.setTemporalRevision(nextPositive(state.getTemporalRevision()));
        }
    }

    private static WorkTicket captureTicketLocked(
            MarketEconomyState state,
            TicketKind kind,
            int dependencyMask,
            AoTDRuntimeEpoch.Stamp stamp) {
        return new WorkTicket(
                state.getMarketId(),
                state.getMarket(),
                state.getDeliveredGeneration(),
                state.getStructuralGeneration(),
                state.getDirtyGeneration(),
                state.getDirtyMask(),
                state.getPriorityHint(),
                state.getFirstDirtyNanos(),
                kind,
                dependencyMask,
                RevisionVector.of(state),
                stamp);
    }

    private static void restoreTicketDirtyLocked(MarketEconomyState state, WorkTicket ticket) {
        restoreMaskMetadataLocked(state, ticket, ticket.dirtyMask);
    }

    private static void restoreMaskMetadataLocked(
            MarketEconomyState state, WorkTicket ticket, int mask) {
        if (mask == 0) return;
        state.setDirtyMask(state.getDirtyMask() | mask);
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
        if (state == null
                || state.isQuarantined()
                || state.getDirtyMask() == 0
                || state.isQueued()
                || state.isSnapshotBuilding()
                || state.isRunning()
                || state.isResultReady()) return;
        state.setQueued(true);
        if (state.getPriorityHint() > PRIORITY_NORMAL) {
            state.setUrgentQueued(true);
            urgent.addLast(state);
        } else {
            state.setUrgentQueued(false);
            normal.addLast(state);
        }
    }

    private static MarketEconomyState pollNextLocked() {
        MarketEconomyState state;
        if (!urgent.isEmpty() && (normal.isEmpty() || urgentBurst < 7)) {
            urgentBurst++;
            state = urgent.pollFirst();
        } else {
            urgentBurst = 0;
            state = normal.pollFirst();
            if (state == null) state = urgent.pollFirst();
        }
        return state;
    }

    private static void removeQueuedLocked(MarketEconomyState state) {
        if (state == null || !state.isQueued()) return;
        if (state.isUrgentQueued()) urgent.remove(state);
        else normal.remove(state);
        state.setQueued(false);
        state.setUrgentQueued(false);
    }

    private static boolean matchesEpochLocked(WorkTicket ticket) {
        return ticket != null && AoTDRuntimeEpoch.isCurrent(ticket.epochStamp);
    }

    private static boolean matchesIdentityLocked(MarketEconomyState state, WorkTicket ticket) {
        return state != null && state.getMarket() == ticket.market;
    }

    private static boolean matchesGenerationsLocked(MarketEconomyState state, WorkTicket ticket) {
        if (!matchesIdentityLocked(state, ticket)) return false;
        RevisionVector current = RevisionVector.of(state);
        return ticket.revisions.matches(current, ticket.dependencyMask);
    }

    private static boolean needsMaterializedVectorLocked(MarketEconomyState state) {
        return state.getMaterializedCommittedStructureRevision() != state.getStructureRevision()
                || state.getMaterializedCommittedRevision() != state.getMaterializedRevision()
                || state.getMaterializedCommittedTemporalRevision() != state.getTemporalRevision();
    }

    private static boolean needsPriceVectorLocked(MarketEconomyState state) {
        return state.getPriceCommittedStructureRevision() != state.getStructureRevision()
                || state.getPriceCommittedMaterializedRevision() != state.getMaterializedRevision()
                || state.getPriceCommittedInputRevision() != state.getPriceInputRevision()
                || state.getPriceCommittedStockpileRevision() != state.getStockpileRevision()
                || state.getPriceCommittedTemporalRevision() != state.getTemporalRevision();
    }

    private static boolean needsTradeVectorLocked(MarketEconomyState state) {
        return state.getTradeCommittedStructureRevision() != state.getStructureRevision()
                || state.getTradeCommittedMaterializedRevision() != state.getMaterializedRevision()
                || state.getTradeCommittedAccessibilityRevision()
                        != state.getAccessibilityRevision()
                || state.getTradeCommittedInputRevision() != state.getTradeInputRevision()
                || state.getTradeCommittedTemporalRevision() != state.getTemporalRevision();
    }

    private static int dependenciesForDirtyMask(int dirtyMask) {
        int dependencies = 0;
        if ((dirtyMask & (SchedulerBridge.DIRTY_STRUCTURE | DIRTY_INITIAL_REGISTRATION)) != 0)
            dependencies |= DEP_STRUCTURE;
        if ((dirtyMask & MATERIALIZED_REVISION_MASK) != 0) dependencies |= DEP_MATERIALIZED;
        if ((dirtyMask & PRICE_REVISION_MASK) != 0) dependencies |= DEP_PRICE_INPUT;
        if ((dirtyMask
                        & (DIRTY_STOCKPILE
                                | SchedulerBridge.DIRTY_STRUCTURE
                                | DIRTY_INITIAL_REGISTRATION))
                != 0) dependencies |= DEP_STOCKPILE;
        if ((dirtyMask & ACCESSIBILITY_REVISION_MASK) != 0) dependencies |= DEP_ACCESSIBILITY;
        if ((dirtyMask & TRADE_REVISION_MASK) != 0) dependencies |= DEP_TRADE_INPUT;
        if ((dirtyMask & DIRTY_TIME_DELIVERED) != 0) dependencies |= DEP_TEMPORAL;
        return dependencies;
    }

    private static void commitVectorsForMaskLocked(MarketEconomyState state, int dirtyMask) {
        if ((dirtyMask & MATERIALIZED_WORK_MASK) != 0) state.commitMaterializedVector();
        if ((dirtyMask & (PRICE_WORK_MASK | PRICE_REVISION_MASK | DIRTY_TIME_DELIVERED)) != 0)
            state.commitPriceVector();
        if ((dirtyMask & TRADE_WORK_MASK) != 0) state.commitTradeVector();
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    public enum RegistryLifecycle {
        EMPTY,
        BUILDING,
        READY
    }

    public enum CommitStatus {
        COMMITTED,
        UNKNOWN_MARKET,
        SNAPSHOT_BUILDING,
        RUNNING,
        RESULT_READY
    }

    private enum TicketKind {
        PRICE,
        GENERAL
    }

    private enum OutputDomain {
        MATERIALIZED,
        TRADE
    }

    public static final class RevisionVector {
        public final long structureRevision;
        public final long materializedRevision;
        public final long priceInputRevision;
        public final long stockpileRevision;
        public final long accessibilityRevision;
        public final long tradeInputRevision;
        public final long temporalRevision;

        private RevisionVector(
                long structureRevision,
                long materializedRevision,
                long priceInputRevision,
                long stockpileRevision,
                long accessibilityRevision,
                long tradeInputRevision,
                long temporalRevision) {
            this.structureRevision = structureRevision;
            this.materializedRevision = materializedRevision;
            this.priceInputRevision = priceInputRevision;
            this.stockpileRevision = stockpileRevision;
            this.accessibilityRevision = accessibilityRevision;
            this.tradeInputRevision = tradeInputRevision;
            this.temporalRevision = temporalRevision;
        }

        static RevisionVector of(MarketEconomyState state) {
            return new RevisionVector(
                    state.getStructureRevision(),
                    state.getMaterializedRevision(),
                    state.getPriceInputRevision(),
                    state.getStockpileRevision(),
                    state.getAccessibilityRevision(),
                    state.getTradeInputRevision(),
                    state.getTemporalRevision());
        }

        boolean matches(RevisionVector other, int dependencies) {
            return ((dependencies & DEP_STRUCTURE) == 0
                            || structureRevision == other.structureRevision)
                    && ((dependencies & DEP_MATERIALIZED) == 0
                            || materializedRevision == other.materializedRevision)
                    && ((dependencies & DEP_PRICE_INPUT) == 0
                            || priceInputRevision == other.priceInputRevision)
                    && ((dependencies & DEP_STOCKPILE) == 0
                            || stockpileRevision == other.stockpileRevision)
                    && ((dependencies & DEP_ACCESSIBILITY) == 0
                            || accessibilityRevision == other.accessibilityRevision)
                    && ((dependencies & DEP_TRADE_INPUT) == 0
                            || tradeInputRevision == other.tradeInputRevision)
                    && ((dependencies & DEP_TEMPORAL) == 0
                            || temporalRevision == other.temporalRevision);
        }

        long fingerprint(int dependencies) {
            long hash = 0xcbf29ce484222325L;
            if ((dependencies & DEP_STRUCTURE) != 0) hash = mix(hash, structureRevision);
            if ((dependencies & DEP_MATERIALIZED) != 0) hash = mix(hash, materializedRevision);
            if ((dependencies & DEP_PRICE_INPUT) != 0) hash = mix(hash, priceInputRevision);
            if ((dependencies & DEP_STOCKPILE) != 0) hash = mix(hash, stockpileRevision);
            if ((dependencies & DEP_ACCESSIBILITY) != 0) hash = mix(hash, accessibilityRevision);
            if ((dependencies & DEP_TRADE_INPUT) != 0) hash = mix(hash, tradeInputRevision);
            if ((dependencies & DEP_TEMPORAL) != 0) hash = mix(hash, temporalRevision);
            return hash == 0L ? 1L : hash;
        }

        private static long mix(long hash, long value) {
            hash ^= value;
            hash *= 0x100000001b3L;
            return hash;
        }

        @Override
        public String toString() {
            return "{structure="
                    + structureRevision
                    + ", materialized="
                    + materializedRevision
                    + ", price="
                    + priceInputRevision
                    + ", stockpile="
                    + stockpileRevision
                    + ", accessibility="
                    + accessibilityRevision
                    + ", trade="
                    + tradeInputRevision
                    + ", temporal="
                    + temporalRevision
                    + '}';
        }
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
        public final RevisionVector revisions;
        public final long validationFingerprint;
        public final AoTDRuntimeEpoch.Stamp epochStamp;
        public final long campaignEpoch;
        public final long economyEpoch;
        public final long batchRevision;
        private final TicketKind kind;
        private final int dependencyMask;

        WorkTicket(
                String marketId,
                Object market,
                long deliveredGeneration,
                long structuralGeneration,
                long dirtyGeneration,
                int dirtyMask,
                int priorityHint,
                long dirtySinceNanos,
                TicketKind kind,
                int dependencyMask,
                RevisionVector revisions,
                AoTDRuntimeEpoch.Stamp epochStamp) {
            this.marketId = marketId;
            this.market = market;
            this.deliveredGeneration = deliveredGeneration;
            this.structuralGeneration = structuralGeneration;
            this.dirtyGeneration = dirtyGeneration;
            this.dirtyMask = dirtyMask;
            this.priorityHint = priorityHint;
            this.dirtySinceNanos = dirtySinceNanos;
            this.kind = kind;
            this.dependencyMask = dependencyMask;
            this.revisions = revisions;
            this.epochStamp = epochStamp;
            this.campaignEpoch = epochStamp == null ? 0L : epochStamp.campaignEpoch;
            this.economyEpoch = epochStamp == null ? 0L : epochStamp.economyEpoch;
            this.batchRevision = epochStamp == null ? 0L : epochStamp.batchRevision;
            this.validationFingerprint = revisions.fingerprint(dependencyMask);
        }
    }

    public static final class StateSnapshot {
        public final String marketId;
        public final long deliveredGeneration;
        public final long structuralGeneration;
        public final long derivedGeneration;
        public final long priceGeneration;
        public final long dirtyGeneration;
        public final RevisionVector revisions;
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
            revisions = RevisionVector.of(state);
            dirtyMask = state.getDirtyMask();
            priorityHint = state.getPriorityHint();
            queued = state.isQueued();
            busy = state.isSnapshotBuilding() || state.isRunning() || state.isResultReady();
            dirtyAgeNanos =
                    state.getFirstDirtyNanos() == 0L
                            ? 0L
                            : Math.max(0L, System.nanoTime() - state.getFirstDirtyNanos());
            lastComputeNanos = state.getLastComputeNanos();
        }
    }

    public static final class InvariantReport {
        public final int expectedMarkets;
        public final int registeredMarkets;
        public final int states;
        public final int identities;
        public final int queuedEntries;
        public final long registryGeneration;
        public final RegistryLifecycle lifecycle;
        public final int violationCount;
        public final List<String> samples;

        InvariantReport(
                int expectedMarkets,
                int registeredMarkets,
                int states,
                int identities,
                int queuedEntries,
                long registryGeneration,
                RegistryLifecycle lifecycle,
                int violationCount,
                List<String> samples) {
            this.expectedMarkets = expectedMarkets;
            this.registeredMarkets = registeredMarkets;
            this.states = states;
            this.identities = identities;
            this.queuedEntries = queuedEntries;
            this.registryGeneration = registryGeneration;
            this.lifecycle = lifecycle;
            this.violationCount = violationCount;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        }

        public boolean isClean() {
            return violationCount == 0;
        }

        public String summary() {
            return "expectedMarkets="
                    + expectedMarkets
                    + ", registeredMarkets="
                    + registeredMarkets
                    + ", states="
                    + states
                    + ", identities="
                    + identities
                    + ", queuedEntries="
                    + queuedEntries
                    + ", registryGeneration="
                    + registryGeneration
                    + ", lifecycle="
                    + lifecycle
                    + ", violations="
                    + violationCount
                    + (samples.isEmpty() ? "" : ", samples=" + samples);
        }

        @Override
        public String toString() {
            return summary();
        }
    }
}
