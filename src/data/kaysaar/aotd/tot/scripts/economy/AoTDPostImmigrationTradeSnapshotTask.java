package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Captures exact trade inputs after ImmigrationTask and publishes the complete
 * changed set atomically before the global internal-trade cut is opened.
 */
public final class AoTDPostImmigrationTradeSnapshotTask extends MultiFrameTask {
    private static final int MAX_CHANGED_IDS_IN_SUMMARY = 12;
    private static final int MAX_COMMIT_REJECTION_SAMPLES = 3;

    private final ArrayList<MarketAPI> markets;
    private final ArrayList<AoTDTradeManager.PreparedSnapshot> prepared;
    private final String context;
    private int marketIndex;
    private int unchanged;
    private int changed;
    private int failures;
    private int initialChanges;
    private int factionChanges;
    private int accessibilityChanges;
    private int eligibilityChanges;
    private int netProductionChanges;
    private int registryCommitFailures;
    private final ArrayList<String> changedMarketIds = new ArrayList<>();

    /* Not final: legacy serialized tasks restore newly-added fields as null. */
    private EnumMap<MarketRegistry.CommitStatus, Integer> registryCommitStatuses;
    private ArrayList<String> registryCommitSamples;
    private MarketRegistry.InvariantReport registryInvariantReport;

    private boolean commitAttempted;
    private boolean committed;
    private boolean done;
    private final long startedNanos = System.nanoTime();

    public AoTDPostImmigrationTradeSnapshotTask(
            List<MarketAPI> markets, String context) {
        this.markets = new ArrayList<>(markets == null ? List.of() : markets);
        this.prepared = new ArrayList<>(this.markets.size());
        this.context = context == null ? "economy" : context;
        ensureDiagnosticState();
    }

    private void ensureDiagnosticState() {
        if (registryCommitStatuses == null) {
            registryCommitStatuses = new EnumMap<>(MarketRegistry.CommitStatus.class);
        }
        if (registryCommitSamples == null) registryCommitSamples = new ArrayList<>();
    }

    @Override
    public void doNextBatch() {
        ensureDiagnosticState();
        if (done) return;
        if (marketIndex < markets.size()) {
            MarketAPI market = markets.get(marketIndex++);
            AoTDTradeManager.PreparedSnapshot snapshot =
                    AoTDTradeManager.getInstance().preparePostImmigrationSnapshot(market);
            prepared.add(snapshot);
            if (snapshot.failed) {
                failures++;
                Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class).error(
                        "AoTD post-immigration trade snapshot capture failed for market "
                                + snapshot.marketId + "; the previous complete trade cut will be retained. "
                                + snapshot.failure);
            } else if (snapshot.changed) {
                changed++;
                countReasons(snapshot.reasonMask);
                if (changedMarketIds.size() < MAX_CHANGED_IDS_IN_SUMMARY) {
                    changedMarketIds.add(snapshot.marketId + "["
                            + describeReasons(snapshot.reasonMask)
                            + ",fp=" + Long.toUnsignedString(snapshot.fingerprint, 16) + "]");
                }
                AoTDEconomySemanticBaseline.operation(
                        "post-immigration.trade-input-changed", market);
            } else {
                unchanged++;
                AoTDEconomySemanticBaseline.operation(
                        "post-immigration.trade-input-unchanged", market);
            }
            return;
        }

        if (!commitAttempted) {
            commitAttempted = true;
            if (failures == 0) {
                committed = AoTDTradeManager.getInstance()
                        .commitPreparedSnapshots(prepared);
                if (!committed) {
                    failures++;
                    Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class).error(
                            "AoTD post-immigration trade snapshot batch was rejected because "
                                    + "its publication baseline changed before commit; retaining the previous cut.");
                }
            }
            if (committed) commitRegistryState();
            logSummary();
            done = true;
        }
    }

    private static String describeReasons(int reasonMask) {
        StringBuilder result = new StringBuilder();
        appendReason(result, reasonMask, AoTDTradeManager.SnapshotRefreshResult.REASON_INITIAL, "initial");
        appendReason(result, reasonMask, AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION, "faction");
        appendReason(result, reasonMask, AoTDTradeManager.SnapshotRefreshResult.REASON_ACCESSIBILITY, "accessibility");
        appendReason(result, reasonMask, AoTDTradeManager.SnapshotRefreshResult.REASON_ELIGIBILITY, "eligibility");
        appendReason(result, reasonMask, AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION, "net");
        return result.length() == 0 ? "unknown" : result.toString();
    }

    private static void appendReason(
            StringBuilder target, int mask, int flag, String label) {
        if ((mask & flag) == 0) return;
        if (target.length() > 0) target.append('+');
        target.append(label);
    }

    private void countReasons(int reasonMask) {
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_INITIAL) != 0) initialChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION) != 0) factionChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_ACCESSIBILITY) != 0) accessibilityChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_ELIGIBILITY) != 0) eligibilityChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION) != 0) netProductionChanges++;
    }

    private void commitRegistryState() {
        ensureDiagnosticState();
        LinkedHashMap<String, MarketAPI> expected = new LinkedHashMap<>();
        for (MarketAPI market : markets) {
            if (market != null && market.getId() != null) expected.put(market.getId(), market);
        }
        registryInvariantReport = MarketRegistry.auditInvariants(expected);

        for (int i = 0; i < prepared.size(); i++) {
            AoTDTradeManager.PreparedSnapshot snapshot = prepared.get(i);
            MarketAPI market = markets.get(i);
            if (snapshot.changed) {
                AoTDEconomySemanticBaseline.captureTradeSnapshot(
                        "post-immigration.trade-snapshot-committed", market);
                int dirtyMask = MarketRegistry.DIRTY_TRADE;
                int reason = snapshot.reasonMask;
                if ((reason & (AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION
                        | AoTDTradeManager.SnapshotRefreshResult.REASON_ACCESSIBILITY
                        | AoTDTradeManager.SnapshotRefreshResult.REASON_ELIGIBILITY)) != 0) {
                    dirtyMask |= MarketRegistry.DIRTY_ACCESSIBILITY
                            | MarketRegistry.DIRTY_GLOBAL_REVISION;
                }
                if ((reason & AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION) != 0) {
                    dirtyMask |= MarketRegistry.DIRTY_VALUE_STATE
                            | MarketRegistry.DIRTY_PRICE
                            | MarketRegistry.DIRTY_STOCKPILE
                            | SchedulerBridge.DIRTY_DERIVED_ECONOMY;
                }
                MarketRegistry.markDirty(
                        market, dirtyMask, MarketRegistry.PRIORITY_NORMAL);
            }

            MarketRegistry.CommitStatus status =
                    MarketRegistry.commitTradeSnapshotDetailed(
                            market, Math.max(0L, System.nanoTime() - startedNanos));
            registryCommitStatuses.merge(status, 1, Integer::sum);
            if (status != MarketRegistry.CommitStatus.COMMITTED) {
                registryCommitFailures++;
                if (registryCommitSamples.size() < MAX_COMMIT_REJECTION_SAMPLES) {
                    registryCommitSamples.add(status + "["
                            + MarketRegistry.describeCommitState(market) + "]");
                }
                MarketRegistry.markDirty(
                        market, MarketRegistry.DIRTY_TRADE,
                        MarketRegistry.PRIORITY_NORMAL);
            }
        }
    }

    private int registryCommitCount(MarketRegistry.CommitStatus status) {
        ensureDiagnosticState();
        return registryCommitStatuses.getOrDefault(status, 0);
    }

    private void logSummary() {
        ensureDiagnosticState();
        long elapsedMicros = Math.max(0L, System.nanoTime() - startedNanos) / 1_000L;
        String ids = changedMarketIds.isEmpty() ? "[]" : changedMarketIds.toString();
        int omitted = Math.max(0, changed - changedMarketIds.size());
        MarketRegistry.InvariantReport audit = registryInvariantReport;
        Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class).info(
                "AoTD post-immigration trade snapshot phase: context=" + context
                        + ", checked=" + markets.size()
                        + ", changed=" + changed
                        + ", unchanged=" + unchanged
                        + ", failures=" + failures
                        + ", committed=" + committed
                        + ", initial=" + initialChanges
                        + ", faction=" + factionChanges
                        + ", accessibility=" + accessibilityChanges
                        + ", eligibility=" + eligibilityChanges
                        + ", netProduction=" + netProductionChanges
                        + ", registryCommitFailures=" + registryCommitFailures
                        + ", registryCommitCommitted="
                        + registryCommitCount(MarketRegistry.CommitStatus.COMMITTED)
                        + ", registryCommitUnknownMarket="
                        + registryCommitCount(MarketRegistry.CommitStatus.UNKNOWN_MARKET)
                        + ", registryCommitSnapshotBuilding="
                        + registryCommitCount(MarketRegistry.CommitStatus.SNAPSHOT_BUILDING)
                        + ", registryCommitRunning="
                        + registryCommitCount(MarketRegistry.CommitStatus.RUNNING)
                        + ", registryCommitResultReady="
                        + registryCommitCount(MarketRegistry.CommitStatus.RESULT_READY)
                        + ", registryCommitSamples=" + registryCommitSamples
                        + ", registryInvariantViolations="
                        + (audit == null ? -1 : audit.violationCount)
                        + ", registryExpectedMarkets="
                        + (audit == null ? -1 : audit.expectedMarkets)
                        + ", registryRegisteredMarkets="
                        + (audit == null ? -1 : audit.registeredMarkets)
                        + ", registryStates=" + (audit == null ? -1 : audit.states)
                        + ", registryIdentities=" + (audit == null ? -1 : audit.identities)
                        + ", registryQueuedEntries="
                        + (audit == null ? -1 : audit.queuedEntries)
                        + ", registryAuditGeneration="
                        + (audit == null ? -1 : audit.registryGeneration)
                        + ", registryLifecycle="
                        + (audit == null ? MarketRegistry.getRegistryLifecycle() : audit.lifecycle)
                        + ", changedMarkets=" + ids
                        + (omitted > 0 ? ", omittedChangedMarkets=" + omitted : "")
                        + ", elapsedMicros=" + elapsedMicros
                        + ", registry=" + MarketRegistry.statusSummary());
    }

    @Override public boolean isDone() { return done; }

    @Override
    public String getLoggingIdentifier() {
        return "AoTD-Post-Immigration-Trade-Snapshot";
    }
}
