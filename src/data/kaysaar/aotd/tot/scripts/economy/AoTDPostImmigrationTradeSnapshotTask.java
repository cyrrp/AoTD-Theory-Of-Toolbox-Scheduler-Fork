package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Captures exact trade inputs after ImmigrationTask and publishes the complete changed set
 * atomically before the global internal-trade cut is opened.
 */
public final class AoTDPostImmigrationTradeSnapshotTask extends MultiFrameTask {
    private static final int MAX_CHANGED_IDS_IN_SUMMARY = 12;
    private static final int MAX_COMMIT_REJECTION_SAMPLES = 3;
    private static final int MATERIALIZED_REFRESH_DIRTY_MASK =
            MarketRegistry.DIRTY_VALUE_STATE
                    | MarketRegistry.DIRTY_PRICE
                    | MarketRegistry.DIRTY_STOCKPILE
                    | SchedulerBridge.DIRTY_DERIVED_ECONOMY;

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
    private int committedNetFastPaths;
    private int liveNetFallbacks;
    private int deferredRegistryMarkets;
    private int materializedRefreshRequired;
    private int staleProofRecaptures;
    private int staleProofCommitRejections;
    private int batchCommitRejections;
    private int batchPublicationFailures;
    private int registryCommitFailures;
    private int registryBookkeepingFailures;
    private int registryCommitProgress;
    private final ArrayList<String> changedMarketIds = new ArrayList<>();

    /* Not final: legacy serialized tasks restore newly-added fields as null. */
    private EnumMap<MarketRegistry.CommitStatus, Integer> registryCommitStatuses;
    private EnumMap<AoTDMarketData.PostImmigrationFallbackReason, Integer> fallbackReasons;
    private ArrayList<String> registryCommitSamples;
    private MarketRegistry.InvariantReport registryInvariantReport;

    private boolean commitAttempted;
    private boolean staleProofRecaptureAttempted;
    private boolean committed;
    private boolean done;
    private final long startedNanos = System.nanoTime();
    private transient AoTDRuntimeEpoch.Stamp epochStamp;

    public AoTDPostImmigrationTradeSnapshotTask(List<MarketAPI> markets, String context) {
        this.markets = new ArrayList<>(markets == null ? List.of() : markets);
        this.prepared = new ArrayList<>(this.markets.size());
        this.context = context == null ? "economy" : context;
        this.epochStamp =
                AoTDRuntimeEpoch.captureBatch("post-immigration-trade-snapshot:" + this.context);
        ensureDiagnosticState();
    }

    private void ensureDiagnosticState() {
        if (registryCommitStatuses == null) {
            registryCommitStatuses = new EnumMap<>(MarketRegistry.CommitStatus.class);
        }
        if (fallbackReasons == null) {
            fallbackReasons = new EnumMap<>(AoTDMarketData.PostImmigrationFallbackReason.class);
        }
        if (registryCommitSamples == null) registryCommitSamples = new ArrayList<>();
    }

    @Override
    public void doNextBatch() {
        if (!ensureCurrentEpoch()) return;
        ensureDiagnosticState();
        if (done) return;
        if (marketIndex < markets.size()) {
            MarketAPI market = markets.get(marketIndex++);
            AoTDTradeManager.PreparedSnapshot snapshot =
                    AoTDTradeManager.getInstance().preparePostImmigrationSnapshot(market);
            prepared.add(snapshot);
            if (snapshot.failed) {
                failures++;
                Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                        .error(
                                "AoTD post-immigration trade snapshot capture failed for market "
                                        + snapshot.marketId
                                        + "; the previous complete trade cut will be retained. "
                                        + snapshot.failure);
            } else if (snapshot.changed) {
                AoTDEconomySemanticBaseline.operation(
                        "post-immigration.trade-input-changed", market);
            } else {
                AoTDEconomySemanticBaseline.operation(
                        "post-immigration.trade-input-unchanged", market);
            }
            return;
        }

        if (!commitAttempted) {
            recaptureStalePreparedSnapshotsOnce();
            recomputePreparedDiagnostics();
            commitAttempted = true;
            try {
                if (failures == 0) {
                    try {
                        committed =
                                AoTDTradeManager.getInstance().commitPreparedSnapshots(prepared);
                    } catch (RuntimeException publicationFailure) {
                        batchPublicationFailures++;
                        failures++;
                        Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                                .error(
                                        "AoTD post-immigration trade snapshot publication failed; "
                                                + "the manager rolled back to the previous complete cut.",
                                        publicationFailure);
                    }
                    if (!committed && batchPublicationFailures == 0) {
                        batchCommitRejections++;
                        if (hasStalePreparedProof()) staleProofCommitRejections++;
                        failures++;
                        Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                                .error(
                                        "AoTD post-immigration trade snapshot batch was rejected because "
                                                + "its publication baseline changed before commit; retaining the previous cut.");
                    }
                }
                if (committed) {
                    try {
                        commitRegistryState();
                    } catch (RuntimeException bookkeepingFailure) {
                        registryBookkeepingFailures++;
                        failures++;
                        conservativelyRequeueUnfinishedRegistryState();
                        Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                                .error(
                                        "AoTD published the post-immigration trade cut, but registry bookkeeping failed; "
                                                + "unfinished markets were conservatively requeued.",
                                        bookkeepingFailure);
                    }
                }
            } catch (RuntimeException phaseFailure) {
                failures++;
                if (committed) {
                    registryBookkeepingFailures++;
                    conservativelyRequeueUnfinishedRegistryState();
                }
                Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                        .error(
                                "AoTD post-immigration trade snapshot finalization failed; "
                                        + "recoverable scheduler work was retained.",
                                phaseFailure);
            } finally {
                // A contained RuntimeException must never leave this task permanently live with
                // commitAttempted=true. The manager either published the entire cut or rolled it
                // back; registry failures retain dirty work for scheduler recovery.
                done = true;
                try {
                    logSummary();
                } catch (RuntimeException summaryFailure) {
                    failures++;
                    Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                            .error(
                                    "AoTD post-immigration trade snapshot summary logging failed.",
                                    summaryFailure);
                }
            }
        }
    }

    /** Rebuilds all final-cut diagnostics after stale entries may have been recaptured. */
    private void recomputePreparedDiagnostics() {
        unchanged = 0;
        changed = 0;
        initialChanges = 0;
        factionChanges = 0;
        accessibilityChanges = 0;
        eligibilityChanges = 0;
        netProductionChanges = 0;
        committedNetFastPaths = 0;
        liveNetFallbacks = 0;
        materializedRefreshRequired = 0;
        changedMarketIds.clear();
        fallbackReasons.clear();

        for (AoTDTradeManager.PreparedSnapshot snapshot : prepared) {
            if (snapshot == null || snapshot.failed) continue;
            if (snapshot.usedCommittedNet) {
                committedNetFastPaths++;
            } else {
                liveNetFallbacks++;
                if (snapshot.fallbackReason != null) {
                    fallbackReasons.merge(snapshot.fallbackReason, 1, Integer::sum);
                }
            }
            if (snapshot.requiresMaterializedRefresh) materializedRefreshRequired++;
            if (!snapshot.changed) {
                unchanged++;
                continue;
            }
            changed++;
            countReasons(snapshot.reasonMask);
            if (changedMarketIds.size() < MAX_CHANGED_IDS_IN_SUMMARY) {
                changedMarketIds.add(
                        snapshot.marketId
                                + "["
                                + describeReasons(snapshot.reasonMask)
                                + ",fp="
                                + Long.toUnsignedString(snapshot.fingerprint, 16)
                                + "]");
            }
        }
    }

    private boolean hasStalePreparedProof() {
        if (MarketRegistry.getRegistryLifecycle() != MarketRegistry.RegistryLifecycle.READY) {
            return false;
        }
        AoTDTradeManager manager = AoTDTradeManager.getInstance();
        for (AoTDTradeManager.PreparedSnapshot snapshot : prepared) {
            if (!manager.isPreparedSnapshotProofCurrent(snapshot)) return true;
        }
        return false;
    }

    private void recaptureStalePreparedSnapshotsOnce() {
        if (staleProofRecaptureAttempted
                || failures != 0
                || MarketRegistry.getRegistryLifecycle() == MarketRegistry.RegistryLifecycle.EMPTY)
            return;
        staleProofRecaptureAttempted = true;
        AoTDTradeManager manager = AoTDTradeManager.getInstance();
        for (int i = 0; i < prepared.size(); i++) {
            AoTDTradeManager.PreparedSnapshot previous = prepared.get(i);
            if (manager.isPreparedSnapshotProofCurrent(previous)) continue;
            AoTDTradeManager.PreparedSnapshot refreshed =
                    manager.preparePostImmigrationSnapshot(markets.get(i));
            prepared.set(i, refreshed);
            staleProofRecaptures++;
            if (refreshed.failed) {
                failures++;
                Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                        .error(
                                "AoTD stale trade-input recapture failed for market "
                                        + refreshed.marketId
                                        + "; retaining the previous complete trade cut. "
                                        + refreshed.failure);
            }
        }
    }

    private boolean ensureCurrentEpoch() {
        if (epochStamp != null && AoTDRuntimeEpoch.isCurrent(epochStamp)) return true;
        if (prepared != null) prepared.clear();
        done = true;
        AoTDEconomySemanticBaseline.operation("post-immigration.stale-epoch-task-dropped", 1L);
        return false;
    }

    /** Releases a partially captured cut that was invalidated by save cleanup/restore. */
    void discardRuntimeStateAfterSave() {
        if (prepared != null) prepared.clear();
        done = true;
        AoTDEconomySemanticBaseline.operation(
                "post-immigration.save-invalidated-snapshot-dropped", 1L);
    }

    private static String describeReasons(int reasonMask) {
        StringBuilder result = new StringBuilder();
        appendReason(
                result,
                reasonMask,
                AoTDTradeManager.SnapshotRefreshResult.REASON_INITIAL,
                "initial");
        appendReason(
                result,
                reasonMask,
                AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION,
                "faction");
        appendReason(
                result,
                reasonMask,
                AoTDTradeManager.SnapshotRefreshResult.REASON_ACCESSIBILITY,
                "accessibility");
        appendReason(
                result,
                reasonMask,
                AoTDTradeManager.SnapshotRefreshResult.REASON_ELIGIBILITY,
                "eligibility");
        appendReason(
                result,
                reasonMask,
                AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION,
                "net");
        return result.length() == 0 ? "unknown" : result.toString();
    }

    private static void appendReason(StringBuilder target, int mask, int flag, String label) {
        if ((mask & flag) == 0) return;
        if (target.length() > 0) target.append('+');
        target.append(label);
    }

    private void countReasons(int reasonMask) {
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_INITIAL) != 0)
            initialChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION) != 0)
            factionChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_ACCESSIBILITY) != 0)
            accessibilityChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_ELIGIBILITY) != 0)
            eligibilityChanges++;
        if ((reasonMask & AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION) != 0)
            netProductionChanges++;
    }

    private void commitRegistryState() {
        ensureDiagnosticState();
        registryCommitProgress = 0;
        if (MarketRegistry.getRegistryLifecycle() == MarketRegistry.RegistryLifecycle.EMPTY) {
            // The trade-manager batch is already committed atomically. During initial-load
            // bootstrap there is no registry state to update; replaceAllMarkets() will create and
            // initial-dirty every market shortly afterwards. Avoid a guaranteed UNKNOWN pass.
            deferredRegistryMarkets = prepared.size();
            AoTDEconomySemanticBaseline.operation(
                    "post-immigration.registry-commit-deferred-empty", deferredRegistryMarkets);
            return;
        }

        LinkedHashMap<String, MarketAPI> expected = new LinkedHashMap<>();
        for (MarketAPI market : markets) {
            if (market != null && market.getId() != null) expected.put(market.getId(), market);
        }
        // A UI-local task deliberately contains only one market; treating that
        // subset as the complete economy would manufacture thousands of false
        // registry violations. Use the exact economy comparison only for a full
        // set and retain the internal registry audit for subsets.
        registryInvariantReport =
                expected.size() == MarketRegistry.size()
                        ? MarketRegistry.auditInvariants(expected)
                        : MarketRegistry.auditInvariants();

        for (int i = 0; i < prepared.size(); i++) {
            AoTDTradeManager.PreparedSnapshot snapshot = prepared.get(i);
            MarketAPI market = markets.get(i);
            int dirtyMask = 0;
            int materializedDirtyMask =
                    snapshot.requiresMaterializedRefresh ? MATERIALIZED_REFRESH_DIRTY_MASK : 0;
            if (snapshot.changed) {
                AoTDEconomySemanticBaseline.captureTradeSnapshot(
                        "post-immigration.trade-snapshot-committed", market);
                dirtyMask = MarketRegistry.DIRTY_TRADE;
                int reason = snapshot.reasonMask;
                if ((reason
                                & (AoTDTradeManager.SnapshotRefreshResult.REASON_FACTION
                                        | AoTDTradeManager.SnapshotRefreshResult
                                                .REASON_ACCESSIBILITY
                                        | AoTDTradeManager.SnapshotRefreshResult
                                                .REASON_ELIGIBILITY))
                        != 0) {
                    dirtyMask |=
                            MarketRegistry.DIRTY_ACCESSIBILITY
                                    | MarketRegistry.DIRTY_GLOBAL_REVISION;
                }
                if ((reason & AoTDTradeManager.SnapshotRefreshResult.REASON_NET_PRODUCTION) != 0) {
                    if (snapshot.usedCommittedNet) {
                        // The aggregate was just proven authoritative. Only its downstream price
                        // consumers need work; invalidating materialization here would destroy the
                        // proof and force an unnecessary CxI pass in the next fixed-point step.
                        dirtyMask |= MarketRegistry.DIRTY_PRICE | MarketRegistry.DIRTY_STOCKPILE;
                    } else if (!snapshot.requiresMaterializedRefresh) {
                        dirtyMask |= MATERIALIZED_REFRESH_DIRTY_MASK;
                    }
                }
            }

            MarketRegistry.CommitStatus status =
                    MarketRegistry.commitTradeSnapshotDetailed(
                            market,
                            snapshot.tradeCaptureProof,
                            dirtyMask,
                            materializedDirtyMask,
                            MarketRegistry.PRIORITY_NORMAL,
                            Math.max(0L, System.nanoTime() - startedNanos));
            registryCommitStatuses.merge(status, 1, Integer::sum);
            if (status != MarketRegistry.CommitStatus.COMMITTED) {
                registryCommitFailures++;
                if (registryCommitSamples.size() < MAX_COMMIT_REJECTION_SAMPLES) {
                    registryCommitSamples.add(
                            status + "[" + MarketRegistry.describeCommitState(market) + "]");
                }
                if (status != MarketRegistry.CommitStatus.STALE_INPUT) {
                    MarketRegistry.markDirty(
                            market, MarketRegistry.DIRTY_TRADE, MarketRegistry.PRIORITY_NORMAL);
                }
            }
            registryCommitProgress = i + 1;
        }
    }

    private void conservativelyRequeueUnfinishedRegistryState() {
        if (MarketRegistry.getRegistryLifecycle() == MarketRegistry.RegistryLifecycle.EMPTY) return;
        int repairMask =
                MarketRegistry.DIRTY_TRADE
                        | MarketRegistry.DIRTY_ACCESSIBILITY
                        | MarketRegistry.DIRTY_GLOBAL_REVISION
                        | MATERIALIZED_REFRESH_DIRTY_MASK;
        for (int i = Math.max(0, registryCommitProgress); i < markets.size(); i++) {
            MarketAPI market = markets.get(i);
            if (market == null) continue;
            try {
                MarketRegistry.markDirty(market, repairMask, MarketRegistry.PRIORITY_NORMAL);
            } catch (RuntimeException requeueFailure) {
                Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                        .error(
                                "AoTD could not requeue registry recovery for market index "
                                        + i
                                        + ".",
                                requeueFailure);
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
        Global.getLogger(AoTDPostImmigrationTradeSnapshotTask.class)
                .info(
                        "AoTD post-immigration trade snapshot phase: context="
                                + context
                                + ", checked="
                                + markets.size()
                                + ", changed="
                                + changed
                                + ", unchanged="
                                + unchanged
                                + ", failures="
                                + failures
                                + ", committed="
                                + committed
                                + ", initial="
                                + initialChanges
                                + ", faction="
                                + factionChanges
                                + ", accessibility="
                                + accessibilityChanges
                                + ", eligibility="
                                + eligibilityChanges
                                + ", netProduction="
                                + netProductionChanges
                                + ", committedNetFastPaths="
                                + committedNetFastPaths
                                + ", liveNetFallbacks="
                                + liveNetFallbacks
                                + ", fallbackReasons="
                                + fallbackReasons
                                + ", materializedRefreshRequired="
                                + materializedRefreshRequired
                                + ", staleProofRecaptures="
                                + staleProofRecaptures
                                + ", staleProofCommitRejections="
                                + staleProofCommitRejections
                                + ", batchCommitRejections="
                                + batchCommitRejections
                                + ", batchPublicationFailures="
                                + batchPublicationFailures
                                + ", deferredRegistryMarkets="
                                + deferredRegistryMarkets
                                + ", registryCommitFailures="
                                + registryCommitFailures
                                + ", registryBookkeepingFailures="
                                + registryBookkeepingFailures
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
                                + ", registryCommitStaleInput="
                                + registryCommitCount(MarketRegistry.CommitStatus.STALE_INPUT)
                                + ", registryCommitSamples="
                                + registryCommitSamples
                                + ", registryInvariantViolations="
                                + (audit == null ? -1 : audit.violationCount)
                                + ", registryExpectedMarkets="
                                + (audit == null ? -1 : audit.expectedMarkets)
                                + ", registryRegisteredMarkets="
                                + (audit == null ? -1 : audit.registeredMarkets)
                                + ", registryStates="
                                + (audit == null ? -1 : audit.states)
                                + ", registryIdentities="
                                + (audit == null ? -1 : audit.identities)
                                + ", registryQueuedEntries="
                                + (audit == null ? -1 : audit.queuedEntries)
                                + ", registryAuditGeneration="
                                + (audit == null ? -1 : audit.registryGeneration)
                                + ", registryLifecycle="
                                + (audit == null
                                        ? MarketRegistry.getRegistryLifecycle()
                                        : audit.lifecycle)
                                + ", changedMarkets="
                                + ids
                                + (omitted > 0 ? ", omittedChangedMarkets=" + omitted : "")
                                + ", elapsedMicros="
                                + elapsedMicros
                                + ", registry="
                                + MarketRegistry.statusSummary());
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public String getLoggingIdentifier() {
        return "AoTD-Post-Immigration-Trade-Snapshot";
    }
}
