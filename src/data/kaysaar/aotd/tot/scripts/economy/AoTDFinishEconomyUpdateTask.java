package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI.EconomyUpdateListener;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDInternalTradeBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Pure global internal-trade settlement over one immutable committed cut. */
public class AoTDFinishEconomyUpdateTask extends FinishEconomyUpdateTask {
    private static final int CHUNK_SIZE = 1;
    private final Economy economy;
    private ArrayList<Future<?>> futures = new ArrayList<>();
    private AoTDGlobalEconomyCoordinator.Boundary boundary;
    private AoTDInternalTradeBatch batch;
    private AoTDRuntimeEpoch.Stamp epochStamp;
    private int sequentialIndex;
    private boolean submitted;
    private boolean computed;
    private boolean done;

    public AoTDFinishEconomyUpdateTask(Economy economy) {
        super(economy);
        this.economy = economy;
        this.epochStamp = AoTDRuntimeEpoch.captureBatch("finish-economy-trade");
    }

    public void doForPlayerOnly() {
        ensureRuntimeCollections();
        if (!ensureCurrentEpoch()) return;
        openCut();
        while (sequentialIndex < batch.size()) batch.computeFaction(sequentialIndex++);
        computed = true;
        commitAndClose();
    }

    @Override
    public void doNextBatch() {
        ensureRuntimeCollections();
        if (done) return;
        if (!ensureCurrentEpoch()) return;
        if (boundary == null) {
            openCut();
            return;
        }
        if (!submitted) {
            if (AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION && batch.size() > 0) {
                futures.addAll(
                        AoTDWorkerManager.submitDynamicBatch(
                                "AoTD pure internal trade",
                                epochStamp,
                                batch.size(),
                                CHUNK_SIZE,
                                batch::computeFaction));
            }
            submitted = true;
            if (batch.size() == 0) computed = true;
            return;
        }
        if (!computed) {
            if (AoTdMainWorkTask2.ENABLE_MULTITHREADED_VERSION) {
                if (!AoTDWorkerManager.areDone(futures)) return;
                awaitWorkersAndRetryInfrastructureFailures();
                computed = true;
            } else {
                int end = Math.min(batch.size(), sequentialIndex + 1);
                while (sequentialIndex < end) batch.computeFaction(sequentialIndex++);
                if (sequentialIndex >= batch.size()) computed = true;
                return;
            }
        }
        commitAndClose();
    }

    private void openCut() {
        boundary =
                AoTDGlobalEconomyCoordinator.beginCommittedCut(
                        AoTDGlobalEconomyCoordinator.BOUNDARY_INTERNAL_TRADE, false, epochStamp);
        batch = boundary.cut.internalTradeBatch;
        AoTDEconomySemanticBaseline.operation("global-cut.open", 1L);
    }

    /** Infrastructure failure retries the same pure DTO algorithm sequentially. */
    private void awaitWorkersAndRetryInfrastructureFailures() {
        boolean infrastructureFailure = false;
        for (Future<?> future : futures) {
            if (future == null) continue;
            try {
                future.get();
            } catch (Exception failure) {
                infrastructureFailure = true;
                Global.getLogger(AoTDFinishEconomyUpdateTask.class)
                        .warn(
                                "AoTD internal-trade worker infrastructure failed; retrying pure DTO work sequentially.",
                                failure);
            }
        }
        if (infrastructureFailure && AoTDRuntimeEpoch.isCurrent(epochStamp)) {
            for (int i = 0; i < batch.size(); i++) {
                if (batch.resultAt(i) == null) batch.computeFaction(i);
            }
        }
    }

    private void commitAndClose() {
        if (!AoTDRuntimeEpoch.isCurrent(epochStamp)) {
            discardStaleEpochTask();
            return;
        }
        boolean modelFailure = false;
        for (int i = 0; i < batch.size(); i++) {
            AoTDInternalTradeBatch.FactionResult result = batch.resultAt(i);
            if (result == null || result.failure != null) {
                modelFailure = true;
                if (result != null && result.failure != null) {
                    Global.getLogger(AoTDFinishEconomyUpdateTask.class)
                            .error(
                                    "AoTD pure internal-trade model failed for faction "
                                            + result.factionId
                                            + "; keeping the previous committed settlement.",
                                    result.failure);
                }
            }
        }
        boolean committed =
                !modelFailure
                        && AoTDTradeManager.getInstance().commitInternalTrade(boundary.cut, batch);
        if (committed) {
            refreshPlayerContractPredictionsOnMainThread();
            notifyEconomyListeners();
            AoTDEconomySemanticBaseline.operation("global-cut.committed", 1L);
        } else {
            AoTDEconomySemanticBaseline.operation("global-cut.rejected", 1L);
        }
        boundary.close();
        AoTDEconomySemanticBaseline.operation("global-cut.closed", 1L);
        done = true;
    }

    private void ensureRuntimeCollections() {
        if (futures == null) futures = new ArrayList<>();
    }

    private boolean ensureCurrentEpoch() {
        if (epochStamp == null) {
            // Old serialized task: do not apply a pre-epoch cut to the loaded economy.
            discardStaleEpochTask();
            return false;
        }
        if (!AoTDRuntimeEpoch.isCurrent(epochStamp)) {
            discardStaleEpochTask();
            return false;
        }
        return true;
    }

    private void discardStaleEpochTask() {
        for (Future<?> future : futures) {
            if (future != null) future.cancel(true);
        }
        futures.clear();
        if (boundary != null) {
            boundary.close();
            boundary = null;
        }
        batch = null;
        done = true;
        AoTDEconomySemanticBaseline.operation("internal-trade.stale-epoch-task-dropped", 1L);
    }

    private void refreshPlayerContractPredictionsOnMainThread() {
        AoTDFactionTradeData playerTrade =
                AoTDTradeManager.getInstance().getFactionTradeData(Factions.PLAYER);
        if (playerTrade != null) playerTrade.refreshContractPredictionsIfPlayerFaction();
    }

    private void notifyEconomyListeners() {
        notifyEconomyListenersOnly(economy, "global-cut");
    }

    /**
     * Publishes the vanilla Economy.nextStep listener boundary without opening a new global
     * internal-trade cut. UI-local refreshes use the last complete committed global cut and notify
     * listeners exactly once.
     */
    public static void notifyEconomyListenersOnly(Economy economy, String context) {
        if (economy == null) return;
        try (AoTDEconomySemanticBaseline.Scope ignored =
                AoTDEconomySemanticBaseline.begin(
                        "finish-economy.notify-listeners", null, context)) {
            List<EconomyAPI.EconomyUpdateListener> listeners =
                    new ArrayList<>(economy.getUpdateListeners());
            int notified = 0;
            for (EconomyUpdateListener listener : listeners) {
                if (listener == null) continue;
                if (listener.isEconomyListenerExpired()) {
                    economy.removeUpdateListener(listener);
                    continue;
                }
                listener.economyUpdated();
                notified++;
            }
            AoTDEconomySemanticBaseline.operation("listener.economyUpdated", notified);
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }
}
