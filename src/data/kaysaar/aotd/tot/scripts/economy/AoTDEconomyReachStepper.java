package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.contract.ContractEconomy;
import com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask;
import com.fs.starfarer.campaign.econ.reach.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.*;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.scripts.trade.tasks.AoTDExternalTradeSolver;
import data.kaysaar.aotd.tot.ui.income.AoTDMonthlyTooltipCreator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AoTDEconomyReachStepper extends ReachEconomyStepper {
    private ReachEconomy econ;
    private State state;
    private float elapsed;
    private float untilNext;
    private int iterLeft;
    private int prevMonth;
    private transient long baselineRevision;
    private boolean monthEndRefreshPending;
    private int pendingPreviousMonth = -1;

    // Small semantic checkpoint serialized instead of ReachEconomyStepper.tasks. Market identities
    // are persisted as stable IDs and rebound to the loaded economy before fresh tasks are built.
    private RuntimeTaskPhase runtimeRestartPhase;
    private ArrayList<String> runtimeRestartMarketIds;
    private int runtimeRestartTaskCount;
    private String runtimeRestartHeadTask;
    private boolean runtimeRestartMarketProgressKnown;
    private boolean runtimeRestartUnknownStage;
    private boolean runtimeRestartStageStarted;
    private AoTdMainWorkTask2.RuntimeRestartMode runtimeRestartMainMode;

    private transient boolean runtimeTaskLoadGuard;
    private transient List<MultiFrameTask> suspendedRuntimeTasks;
    private transient int runtimeTaskSaveSuspensionDepth;

    private static final AtomicLong runtimeTaskRestoreCalls = new AtomicLong();
    private static final AtomicLong runtimeTasksDiscarded = new AtomicLong();
    private static final AtomicLong runtimeIterationsRestarted = new AtomicLong();
    private static final AtomicLong runtimeTaskSaveDetachCalls = new AtomicLong();
    private static final AtomicLong runtimeTaskGraphsDetached = new AtomicLong();
    private static volatile String lastRuntimeTaskRestore = "none";

    private enum RuntimeTaskPhase {
        MAIN_WORK,
        UPDATE_MARKETS,
        IMMIGRATION,
        POST_IMMIGRATION,
        FINISH
    }

    public AoTDEconomyReachStepper(ReachEconomy reachEconomy) {
        super(reachEconomy);
        state = ReachEconomyStepper.State.WAITING;
        elapsed = 1000f;
        untilNext = 3f;
        iterLeft = Economy.NUM_ITER_PER_MONTH;
        prevMonth = -1;
        econ = reachEconomy;
    }

    public void doEconomyTick() {
        doEndOfStepStuff(-1);
    }

    @Override
    protected void doEndOfMonthStuff() {
        final MonthlyReport report = SharedData.getData().getCurrentReport();

        if (report != null) {
            final FDNode marketsNode = report.getNode(MonthlyReport.OUTPOSTS);

            for (MarketAPI market : Misc.getPlayerMarkets(true)) {
                final FDNode mNode = report.getNode(marketsNode, market.getId());
                final FDNode indNode = report.getNode(mNode, "industries");

                for (Industry industry : market.getIndustries()) {
                    final FDNode iNode = report.getNode(indNode, industry.getId());
                    iNode.tooltipCreator = new AoTDMonthlyTooltipCreator();
                }

                final FDNode exportNode = report.getNode(mNode, "exports");
                for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
                    final FDNode eNode = report.getNode(exportNode, com.getId());
                    eNode.income = AoTDTradeManager.getExportIncome(com);
                    eNode.tooltipCreator = new AoTDMonthlyTooltipCreator();
                }
            }
        }

        AoTDTradeManager.endOfMonth = false;
        super.doEndOfMonthStuff();
    }

    @Override
    public ReachEconomy getEconomy() {
        return econ;
    }

    @Override
    public void setEcon(ReachEconomy reach) {
        econ = reach;
    }

    public void performBeforeMonthEnds(int prevMonth) {
        try (AoTDGlobalEconomyCoordinator.Boundary boundary =
                        AoTDGlobalEconomyCoordinator.beginCommittedCut(
                                AoTDGlobalEconomyCoordinator.BOUNDARY_MONTH_END, false);
                AoTDEconomySemanticBaseline.Scope ignored =
                        AoTDEconomySemanticBaseline.begin(
                                "economy.month-end-preparation", null, "month-end")) {
            SectorSurplusConsumptionStats.getInstance().clear();

            for (AoTDFactionTradeData tradeData :
                    AoTDTradeManager.getInstance().getAllFactionTradeData().values()) {
                tradeData.doEndOfMonthStuffForHistory(prevMonth);
            }

            if (!AoTDEconomy.runningPrePlayerEconomy) {

                // NEW: contracts phase (after internal, before external)
                AoTDTradeContractManager.getInstance().runMonthlyContracts();

                // Build external index from remainingNet (post-internal, post-contract)
                final AoTDSectorExternalIndex idx = new AoTDSectorExternalIndex();
                for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                    final AoTDFactionTradeData f =
                            AoTDTradeManager.getInstance()
                                    .getFactionTradeData(market.getFactionId());
                    final AoTDMarketData md = f.getTradeData().get(market.getId());
                    if (md == null) continue;

                    md.resetExternalResults(); // excess-exported tracking for this month
                    if (!market.hasSpaceport()
                            || market.getAccessibilityMod().computeEffective(0f) <= 0f) continue;
                    idx.addMarket(market, md);
                }

                new AoTDExternalTradeSolver().runMonthEndExternalTrade(idx);

                // 5) Apply leftover deficit/excess to AoTDExcDefData
                final float durDays = 31;

                for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                    AoTDFactionTradeData f =
                            AoTDTradeManager.getInstance()
                                    .getFactionTradeData(market.getFactionId());

                    AoTDMarketData md = f.getTradeData().get(market.getId());
                    if (md == null) continue;
                    AoTDIndustryData.getInstance(market).applyEndOfMonthChange(market);
                    for (CommodityOnMarketAPI c : market.getAllCommodities()) {
                        if (!(c instanceof AoTDCommodityOnMarket com)) continue;
                        com.getExcDefData().clearExternalTrade(com);
                        String commodityId = com.getId();
                        int r = md.getRemainingNet(commodityId);

                        int deficit = Math.max(0, -r);
                        int excess = Math.max(0, r);

                        com.getExcDefData().recordDemandForThisMonth(com);
                        com.getExcDefData().applyExternalTrade(deficit, excess, durDays, com);
                    }
                }
            } else {
                for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                    AoTDIndustryData.getInstance(marketAPI).applyEndOfMonthChange(marketAPI);
                }
            }
        }
    }

    @Override
    public void nextFrame(float delta) {
        // readResolve() drops process-local task objects before the campaign epoch is rebound.
        // Never recreate work in the short lifecycle window before onGameLoad installs the new
        // epoch and registry.
        if (runtimeTaskLoadGuard || runtimeTaskSaveSuspensionDepth > 0) return;

        elapsed += delta;
        ContractEconomy.DEBUG = false;

        if (state == ReachEconomyStepper.State.WAITING) {
            final int month = Global.getSector().getClock().getMonth();
            if (month != prevMonth) {
                pendingPreviousMonth = this.prevMonth;
                this.prevMonth = month;
                iterLeft = Economy.NUM_ITER_PER_MONTH;

                final float daysInThisMonth =
                        getNumDaysInCurrMonth() - Global.getSector().getClock().getDay();
                untilNext = daysInThisMonth / ((float) Economy.NUM_ITER_PER_MONTH + 0f);
                elapsed = 0f;

                // First make Prepatcher's pending market time part of the live
                // market state. Delivery callbacks dirty the affected markets.
                AoTDGlobalEconomyCoordinator.flushDeliveredTimeForBoundary(
                        AoTDGlobalEconomyCoordinator.BOUNDARY_MONTH_END);
                // Then run the normal local pipeline before opening the month-end cut.
                monthEndRefreshPending = true;
                state = ReachEconomyStepper.State.DOING_TASKS;
                tasks = null;
            }
        }

        if (state == ReachEconomyStepper.State.WAITING && elapsed >= untilNext && iterLeft > 0) {
            --iterLeft;
            state = ReachEconomyStepper.State.DOING_TASKS;
            tasks = null;
            elapsed = 0f;
        }

        if (state == ReachEconomyStepper.State.DOING_TASKS) {
            if (tasks == null) createTasks();
            if (isDone()) return;

            final MultiFrameTask firstTask = (MultiFrameTask) tasks.get(0);
            firstTask.advance(delta);
            if (firstTask.isDone()) {
                tasks.remove(0);
            }

            if (isDone()) {
                if (monthEndRefreshPending) {
                    performBeforeMonthEnds(pendingPreviousMonth);
                    AoTDTradeManager.endOfMonth = true;
                    doEndOfStepStuff(Economy.NUM_ITER_PER_MONTH - 1);
                    doEndOfMonthStuff();
                    monthEndRefreshPending = false;
                    pendingPreviousMonth = -1;
                    elapsed = untilNext / 2f;
                } else if (iterLeft > 0) {
                    doEndOfStepStuff(Economy.NUM_ITER_PER_MONTH - iterLeft - 1);
                }

                if (baselineRevision > 0L) {
                    AoTDEconomySemanticBaseline.endEconomyRevision(
                            baselineRevision, "iteration-complete");
                    baselineRevision = 0L;
                }
                state = ReachEconomyStepper.State.WAITING;
            }
        }
    }

    private void createTasks() {
        tasks = new ArrayList<>();
        baselineRevision =
                AoTDEconomySemanticBaseline.beginEconomyRevision("reach-stepper-iteration");
        final RuntimeTaskPhase startPhase =
                runtimeRestartPhase == null ? RuntimeTaskPhase.MAIN_WORK : runtimeRestartPhase;
        final boolean iterationsDone = iterLeft <= 0 || monthEndRefreshPending;
        final MainWorkTask.EconWorkParams mainWork = new MainWorkTask.EconWorkParams();
        mainWork.withIncomeAndUpkeep = true;
        mainWork.withStockpileUpdate = iterationsDone;

        final Economy mainEcon = (Economy) Global.getSector().getEconomy();
        final ArrayList<MarketAPI> allMarkets = new ArrayList<>(econ.getMarkets());
        final List<MarketAPI> restartMarkets = resolveRestartMarkets(startPhase, allMarkets);
        final AoTdMainWorkTask2.RuntimeRestartMode mainRestartMode =
                effectiveMainRestartMode(startPhase, runtimeRestartStageStarted);

        if (startPhase == RuntimeTaskPhase.MAIN_WORK) {
            if (mainRestartMode == AoTdMainWorkTask2.RuntimeRestartMode.FULL) {
                addRuntimeTask(
                        new AoTdMainWorkTask2(allMarkets, econ, mainWork),
                        "economy.task.main-work");
            } else if (mainRestartMode == AoTdMainWorkTask2.RuntimeRestartMode.PRICE_REMAINING) {
                addRuntimeTask(
                        AoTdMainWorkTask2.forRuntimePriceRemaining(
                                allMarkets, restartMarkets, econ, mainWork),
                        "economy.task.main-work-price-remaining");
            } else if (mainRestartMode == AoTdMainWorkTask2.RuntimeRestartMode.LISTENERS_ONLY) {
                addRuntimeTask(
                        AoTdMainWorkTask2.forRuntimeListenersOnly(allMarkets, econ, mainWork),
                        "economy.task.main-work-listeners-only");
            } else {
                // Legacy live tasks have no durable transient-plan proof. Preserve the historical
                // fail-safe: never risk reapplying a price/stockpile result of unknown progress.
                AoTDEconomySemanticBaseline.operation(
                        "economy.task.main-work.legacy-progress-dropped", 1L);
            }
        }
        if (shouldCreateStage(
                startPhase, RuntimeTaskPhase.UPDATE_MARKETS, runtimeRestartStageStarted)) {
            List<MarketAPI> markets =
                    startPhase == RuntimeTaskPhase.UPDATE_MARKETS ? restartMarkets : allMarkets;
            if (!markets.isEmpty()) {
                addRuntimeTask(
                        new AoTDUpdateMarketAgainTask(mainEcon, markets),
                        "economy.task.update-market-again");
            }
        }
        if (shouldCreateStage(
                startPhase, RuntimeTaskPhase.IMMIGRATION, runtimeRestartStageStarted)) {
            List<MarketAPI> markets =
                    startPhase == RuntimeTaskPhase.IMMIGRATION ? restartMarkets : allMarkets;
            if (!markets.isEmpty()) {
                addRuntimeTask(
                        new ImmigrationTask(markets, econ, false), "economy.task.immigration");
            }
        }
        if (shouldCreateStage(
                startPhase, RuntimeTaskPhase.POST_IMMIGRATION, runtimeRestartStageStarted)) {
            // A partially prepared publication is intentionally thrown away. Re-capture the full
            // current economy so the replacement cut remains atomic rather than mixing epochs.
            addRuntimeTask(
                    new AoTDPostImmigrationTradeSnapshotTask(
                            allMarkets,
                            monthEndRefreshPending ? "month-end-refresh" : "regular-iteration"),
                    "economy.task.post-immigration-trade-snapshot");
        }
        if (shouldCreateStage(startPhase, RuntimeTaskPhase.FINISH, runtimeRestartStageStarted)) {
            addRuntimeTask(
                    new AoTDFinishEconomyUpdateTask(mainEcon), "economy.task.finish-global-cut");
        }
        clearRuntimeRestartCheckpoint();
    }

    private void addRuntimeTask(MultiFrameTask task, String operation) {
        tasks.add(task);
        AoTDEconomySemanticBaseline.operation(operation, 1L);
    }

    private static boolean startsAtOrBefore(
            RuntimeTaskPhase startPhase, RuntimeTaskPhase candidate) {
        return startPhase.ordinal() <= candidate.ordinal();
    }

    private static boolean shouldCreateStage(
            RuntimeTaskPhase startPhase,
            RuntimeTaskPhase candidate,
            boolean selectedStageHadStarted) {
        if (!startsAtOrBefore(startPhase, candidate)) return false;
        return candidate != RuntimeTaskPhase.MAIN_WORK || !selectedStageHadStarted;
    }

    private AoTdMainWorkTask2.RuntimeRestartMode effectiveMainRestartMode(
            RuntimeTaskPhase startPhase, boolean selectedStageHadStarted) {
        if (startPhase != RuntimeTaskPhase.MAIN_WORK) {
            return AoTdMainWorkTask2.RuntimeRestartMode.DROP;
        }
        if (runtimeRestartMainMode != null) return runtimeRestartMainMode;
        return selectedStageHadStarted
                ? AoTdMainWorkTask2.RuntimeRestartMode.DROP
                : AoTdMainWorkTask2.RuntimeRestartMode.FULL;
    }

    private String plannedStages(RuntimeTaskPhase startPhase, boolean selectedStageHadStarted) {
        if (startPhase == null) return "none";
        AoTdMainWorkTask2.RuntimeRestartMode mainMode =
                effectiveMainRestartMode(startPhase, selectedStageHadStarted);
        StringBuilder result = new StringBuilder();
        for (RuntimeTaskPhase candidate : RuntimeTaskPhase.values()) {
            boolean create =
                    candidate == RuntimeTaskPhase.MAIN_WORK
                            ? startPhase == RuntimeTaskPhase.MAIN_WORK
                                    && mainMode != AoTdMainWorkTask2.RuntimeRestartMode.DROP
                            : shouldCreateStage(startPhase, candidate, selectedStageHadStarted);
            if (!create) continue;
            if (result.length() > 0) result.append('>');
            result.append(candidate.name());
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private List<MarketAPI> resolveRestartMarkets(
            RuntimeTaskPhase phase, List<MarketAPI> allMarkets) {
        boolean mainPriceRemaining =
                phase == RuntimeTaskPhase.MAIN_WORK
                        && runtimeRestartMainMode
                                == AoTdMainWorkTask2.RuntimeRestartMode.PRICE_REMAINING;
        if (!mainPriceRemaining
                && phase != RuntimeTaskPhase.UPDATE_MARKETS
                && phase != RuntimeTaskPhase.IMMIGRATION) {
            return allMarkets;
        }

        if (!runtimeRestartMarketProgressKnown) {
            Global.getLogger(AoTDEconomyReachStepper.class)
                    .warn(
                            "AoTD could not recover exact "
                                    + phaseName(phase)
                                    + " progress; skipping the stale stage instead of risking "
                                    + "duplicate mod callbacks or population advancement.");
            return List.of();
        }

        ArrayList<MarketAPI> resolved = new ArrayList<>();
        HashMap<String, MarketAPI> marketsById = new HashMap<>();
        for (MarketAPI market : allMarkets) {
            if (market != null && market.getId() != null) marketsById.put(market.getId(), market);
        }
        int unresolved = 0;
        for (String marketId :
                runtimeRestartMarketIds == null ? List.<String>of() : runtimeRestartMarketIds) {
            MarketAPI match = marketsById.get(marketId);
            if (match == null) unresolved++;
            else resolved.add(match);
        }
        if (unresolved > 0) {
            Global.getLogger(AoTDEconomyReachStepper.class)
                    .warn(
                            "AoTD could not rebind "
                                    + unresolved
                                    + " runtime-task market IDs after load; continuing with "
                                    + resolved.size()
                                    + " valid remaining markets.");
        }
        return resolved;
    }

    /**
     * Removes the process-local task object graph from the save image. The live list is retained
     * only long enough to release process-local resources after Starsector restores its transient
     * industry state; continuation uses the compact semantic checkpoint. Calls are nesting-safe.
     */
    public RuntimeTaskSaveReport suspendRuntimeTasksForSave() {
        runtimeTaskSaveSuspensionDepth++;
        if (runtimeTaskSaveSuspensionDepth > 1) {
            return RuntimeTaskSaveReport.nested(
                    "suspend", runtimeTaskSaveSuspensionDepth, suspendedRuntimeTasks);
        }

        suspendedRuntimeTasks = tasks;
        captureRuntimeRestartCheckpoint(tasks);
        tasks = null;
        runtimeTaskSaveDetachCalls.incrementAndGet();
        if (suspendedRuntimeTasks != null) runtimeTaskGraphsDetached.incrementAndGet();
        return new RuntimeTaskSaveReport(
                "suspend",
                runtimeTaskSaveSuspensionDepth,
                true,
                false,
                suspendedRuntimeTasks == null ? 0 : suspendedRuntimeTasks.size(),
                phaseName(runtimeRestartPhase));
    }

    /**
     * Releases the suspended live graph after either save outcome and preserves its semantic suffix
     * for lazy reconstruction. Starsector save restore replaces industry-derived objects and the
     * restore coordinator advances registry revisions, so no captured task input remains valid even
     * in the same process.
     */
    public RuntimeTaskSaveReport resumeRuntimeTasksAfterSave() {
        if (runtimeTaskSaveSuspensionDepth <= 0) {
            return new RuntimeTaskSaveReport("resume", 0, false, false, 0, "none");
        }
        runtimeTaskSaveSuspensionDepth--;
        if (runtimeTaskSaveSuspensionDepth > 0) {
            return RuntimeTaskSaveReport.nested(
                    "resume", runtimeTaskSaveSuspensionDepth, suspendedRuntimeTasks);
        }

        List<MultiFrameTask> restored = suspendedRuntimeTasks;
        suspendedRuntimeTasks = null;
        boolean semanticRestart =
                state == ReachEconomyStepper.State.DOING_TASKS && runtimeRestartPhase != null;
        int discardedTaskCount = restored == null ? 0 : restored.size();
        discardSuspendedRuntimeResources(restored);
        tasks = null;
        if (semanticRestart) {
            endBaselineForTaskGraphDiscard("save-semantic-restart");
            return new RuntimeTaskSaveReport(
                    "resume-semantic-restart",
                    0,
                    false,
                    false,
                    discardedTaskCount,
                    phaseName(runtimeRestartPhase));
        }

        endBaselineForTaskGraphDiscard("save-task-graph-discard");
        clearRuntimeRestartCheckpoint();
        return new RuntimeTaskSaveReport(
                "resume-discard", 0, false, false, discardedTaskCount, "none");
    }

    private static void discardSuspendedRuntimeResources(List<MultiFrameTask> suspended) {
        if (suspended == null) return;
        RuntimeException firstFailure = null;
        // Close a possibly open global cut before abandoning market-local tickets.
        for (int i = suspended.size() - 1; i >= 0; i--) {
            MultiFrameTask task = suspended.get(i);
            try {
                if (task instanceof AoTDFinishEconomyUpdateTask finishTask) {
                    finishTask.discardRuntimeStateAfterSave();
                } else if (task instanceof AoTDPostImmigrationTradeSnapshotTask postTask) {
                    postTask.discardRuntimeStateAfterSave();
                } else if (task instanceof AoTdMainWorkTask2 mainTask) {
                    mainTask.discardUnattemptedRuntimeWorkAfterSave();
                }
            } catch (RuntimeException cleanupFailure) {
                if (firstFailure == null) firstFailure = cleanupFailure;
            }
        }
        if (firstFailure != null) {
            Global.getLogger(AoTDEconomyReachStepper.class)
                    .error(
                            "AoTD could not release every process-local economy task resource after save; "
                                    + "the semantic suffix will still be reconstructed.",
                            firstFailure);
        }
    }

    private void endBaselineForTaskGraphDiscard(String reason) {
        long previousRevision = baselineRevision;
        baselineRevision = 0L;
        if (previousRevision > 0L) {
            AoTDEconomySemanticBaseline.endEconomyRevision(previousRevision, reason);
        }
    }

    private void captureRuntimeRestartCheckpoint(List<?> taskList) {
        // A save can be requested after onGameLoad has accepted a serialized checkpoint but before
        // the first frame lazily creates its fresh suffix. Preserve that accepted checkpoint.
        if (state == ReachEconomyStepper.State.DOING_TASKS
                && taskList == null
                && runtimeRestartPhase != null) {
            runtimeRestartTaskCount = 0;
            runtimeRestartHeadTask = null;
            return;
        }
        clearRuntimeRestartCheckpoint();
        if (state != ReachEconomyStepper.State.DOING_TASKS) return;

        runtimeRestartTaskCount = taskList == null ? 0 : taskList.size();
        runtimeRestartHeadTask = firstTaskName(taskList);
        if (taskList == null) {
            runtimeRestartPhase = RuntimeTaskPhase.MAIN_WORK;
            runtimeRestartMarketProgressKnown = true;
            runtimeRestartStageStarted = false;
            runtimeRestartMainMode = AoTdMainWorkTask2.RuntimeRestartMode.FULL;
            return;
        }
        if (taskList.isEmpty()) {
            // There is no normal callback boundary between removal of the last task and iteration
            // finalization, but FINISH is the safest recovery for a legacy empty-list save.
            runtimeRestartPhase = RuntimeTaskPhase.FINISH;
            runtimeRestartMarketProgressKnown = true;
            runtimeRestartStageStarted = false;
            return;
        }

        Object selectedTask = taskList.get(0);
        runtimeRestartStageStarted = true;
        runtimeRestartPhase = phaseForTask(selectedTask);
        if (runtimeRestartPhase == null) {
            runtimeRestartUnknownStage = true;
            for (int i = 1; i < taskList.size(); i++) {
                RuntimeTaskPhase laterPhase = phaseForTask(taskList.get(i));
                if (laterPhase != null) {
                    runtimeRestartPhase = laterPhase;
                    selectedTask = taskList.get(i);
                    runtimeRestartStageStarted = false;
                    break;
                }
            }
            if (runtimeRestartPhase == null) {
                runtimeRestartPhase = RuntimeTaskPhase.FINISH;
                selectedTask = null;
            }
        }

        if (runtimeRestartPhase == RuntimeTaskPhase.MAIN_WORK) {
            AoTdMainWorkTask2.RuntimeRestartProgress mainProgress =
                    selectedTask instanceof AoTdMainWorkTask2 aotdMain
                            ? aotdMain.runtimeRestartProgress()
                            : AoTdMainWorkTask2.RuntimeRestartProgress.drop();
            runtimeRestartMainMode = mainProgress.mode;
            runtimeRestartMarketProgressKnown = mainProgress.known;
            runtimeRestartMarketIds = marketIds(mainProgress.markets);
            if (runtimeRestartMarketIds == null) {
                runtimeRestartMarketProgressKnown = false;
                runtimeRestartMainMode = AoTdMainWorkTask2.RuntimeRestartMode.DROP;
                runtimeRestartMarketIds = new ArrayList<>();
            }
            return;
        }

        RemainingMarketProgress progress =
                remainingMarketProgress(selectedTask, runtimeRestartPhase);
        runtimeRestartMarketProgressKnown = progress.known;
        runtimeRestartMarketIds = marketIds(progress.markets);
        if (runtimeRestartMarketIds == null) runtimeRestartMarketProgressKnown = false;
    }

    private static RuntimeTaskPhase phaseForTask(Object task) {
        if (task instanceof AoTdMainWorkTask2 || task instanceof MainWorkTask2)
            return RuntimeTaskPhase.MAIN_WORK;
        if (task instanceof AoTDUpdateMarketAgainTask || task instanceof UpdateMarketsAgainTask)
            return RuntimeTaskPhase.UPDATE_MARKETS;
        if (task instanceof ImmigrationTask) return RuntimeTaskPhase.IMMIGRATION;
        if (task instanceof AoTDPostImmigrationTradeSnapshotTask)
            return RuntimeTaskPhase.POST_IMMIGRATION;
        if (task instanceof AoTDFinishEconomyUpdateTask || task instanceof FinishEconomyUpdateTask)
            return RuntimeTaskPhase.FINISH;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static RemainingMarketProgress remainingMarketProgress(
            Object task, RuntimeTaskPhase phase) {
        try {
            if (phase == RuntimeTaskPhase.UPDATE_MARKETS) {
                if (task instanceof AoTDUpdateMarketAgainTask updateTask) {
                    return RemainingMarketProgress.known(
                            updateTask.remainingMarketsForRuntimeRestart());
                }
                if (task instanceof UpdateMarketsAgainTask) {
                    List<MarketAPI> markets =
                            (List<MarketAPI>)
                                    ReflectionUtilis.getPrivateVariableFromSuperClass(
                                            "markets", task);
                    Integer marketIndex =
                            (Integer)
                                    ReflectionUtilis.getPrivateVariableFromSuperClass(
                                            "marketIndex", task);
                    int from = marketIndex == null ? 0 : Math.max(0, marketIndex);
                    if (markets == null || from >= markets.size())
                        return RemainingMarketProgress.known(List.of());
                    return RemainingMarketProgress.known(
                            new ArrayList<>(markets.subList(from, markets.size())));
                }
            } else if (phase == RuntimeTaskPhase.IMMIGRATION && task instanceof ImmigrationTask) {
                List<MarketAPI> markets =
                        (List<MarketAPI>)
                                ReflectionUtilis.getPrivateVariableFromSuperClass("markets", task);
                return RemainingMarketProgress.known(
                        markets == null ? List.of() : new ArrayList<>(markets));
            }
            return RemainingMarketProgress.known(List.of());
        } catch (RuntimeException failure) {
            return RemainingMarketProgress.unknown();
        }
    }

    private static ArrayList<String> marketIds(List<MarketAPI> markets) {
        if (markets == null) return null;
        ArrayList<String> ids = new ArrayList<>(markets.size());
        try {
            for (MarketAPI market : markets) {
                if (market == null || market.getId() == null) return null;
                ids.add(market.getId());
            }
            return ids;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private void clearRuntimeRestartCheckpoint() {
        runtimeRestartPhase = null;
        runtimeRestartMarketIds = null;
        runtimeRestartTaskCount = 0;
        runtimeRestartHeadTask = null;
        runtimeRestartMarketProgressKnown = false;
        runtimeRestartUnknownStage = false;
        runtimeRestartStageStarted = false;
        runtimeRestartMainMode = null;
    }

    private static String phaseName(RuntimeTaskPhase phase) {
        return phase == null ? "none" : phase.name();
    }

    /**
     * Drops any legacy serialized task graph while preserving state, calendar cadence and the
     * semantic suffix checkpoint. Fresh tasks are created lazily after the new epoch is bound.
     */
    public RuntimeTaskRestartReport restartRuntimeTasksAfterLoad(
            AoTDRuntimeEpoch.EpochSnapshot epoch) {
        if (tasks != null) captureRuntimeRestartCheckpoint(tasks);

        int discardedTasks = runtimeRestartTaskCount;
        String discardedHeadTask = runtimeRestartHeadTask;
        boolean loadGuardWasActive = runtimeTaskLoadGuard;
        boolean iterationRestarted = state == ReachEconomyStepper.State.DOING_TASKS;
        if (iterationRestarted && runtimeRestartPhase == null) {
            runtimeRestartPhase = RuntimeTaskPhase.MAIN_WORK;
            runtimeRestartMarketProgressKnown = true;
            runtimeRestartMainMode = AoTdMainWorkTask2.RuntimeRestartMode.FULL;
        }
        RuntimeTaskPhase restartPhase = runtimeRestartPhase;
        int remainingMarketCount =
                runtimeRestartMarketIds == null ? 0 : runtimeRestartMarketIds.size();
        boolean remainingProgressKnown = runtimeRestartMarketProgressKnown;
        boolean unknownStage = runtimeRestartUnknownStage;
        boolean stageHadStarted = runtimeRestartStageStarted;
        String plannedRestartStages = plannedStages(restartPhase, stageHadStarted);
        String mainRestartMode =
                restartPhase == RuntimeTaskPhase.MAIN_WORK
                        ? effectiveMainRestartMode(restartPhase, stageHadStarted).name()
                        : "NONE";

        if (unknownStage) {
            Global.getLogger(AoTDEconomyReachStepper.class)
                    .warn(
                            "AoTD discarded an unknown serialized economy task "
                                    + (discardedHeadTask == null ? "<null>" : discardedHeadTask)
                                    + "; fail-safe restart begins at "
                                    + phaseName(restartPhase)
                                    + " instead of repeating the full economy iteration.");
        }

        // Dropping the list also drops every nested runtime-only graph: prepared snapshots,
        // worker futures, pure DTO batches, boundary handles and process-local nano-time origins.
        tasks = null;
        baselineRevision = 0L;
        runtimeTaskLoadGuard = false;
        runtimeRestartTaskCount = 0;
        runtimeRestartHeadTask = null;
        runtimeRestartUnknownStage = false;
        if (!iterationRestarted) clearRuntimeRestartCheckpoint();

        long campaignEpoch = epoch == null ? 0L : epoch.campaignEpoch;
        long economyEpoch = epoch == null ? 0L : epoch.economyEpoch;
        RuntimeTaskRestartReport report =
                new RuntimeTaskRestartReport(
                        discardedTasks,
                        discardedHeadTask,
                        loadGuardWasActive,
                        iterationRestarted,
                        phaseName(restartPhase),
                        remainingMarketCount,
                        remainingProgressKnown,
                        unknownStage,
                        stageHadStarted,
                        plannedRestartStages,
                        mainRestartMode,
                        state,
                        elapsed,
                        untilNext,
                        iterLeft,
                        prevMonth,
                        monthEndRefreshPending,
                        pendingPreviousMonth,
                        campaignEpoch,
                        economyEpoch);
        runtimeTaskRestoreCalls.incrementAndGet();
        runtimeTasksDiscarded.addAndGet(discardedTasks);
        if (iterationRestarted) runtimeIterationsRestarted.incrementAndGet();
        lastRuntimeTaskRestore = report.summary();
        return report;
    }

    /** XStream compatibility hook; performs no live economy reads or task creation. */
    private Object readResolve() {
        if (tasks != null) captureRuntimeRestartCheckpoint(tasks);
        else if (state == ReachEconomyStepper.State.DOING_TASKS && runtimeRestartPhase == null) {
            runtimeRestartPhase = RuntimeTaskPhase.MAIN_WORK;
            runtimeRestartMarketProgressKnown = true;
            runtimeRestartStageStarted = false;
            runtimeRestartMainMode = AoTdMainWorkTask2.RuntimeRestartMode.FULL;
        }
        tasks = null;
        baselineRevision = 0L;
        runtimeTaskLoadGuard = true;
        suspendedRuntimeTasks = null;
        runtimeTaskSaveSuspensionDepth = 0;
        return this;
    }

    private static String firstTaskName(List<?> taskList) {
        if (taskList == null || taskList.isEmpty() || taskList.get(0) == null) return null;
        return taskList.get(0).getClass().getName();
    }

    public static String runtimeTaskRestoreStatusSummary() {
        return "restoreCalls="
                + runtimeTaskRestoreCalls.get()
                + ", discardedTasks="
                + runtimeTasksDiscarded.get()
                + ", restartedIterations="
                + runtimeIterationsRestarted.get()
                + ", saveDetachCalls="
                + runtimeTaskSaveDetachCalls.get()
                + ", detachedGraphs="
                + runtimeTaskGraphsDetached.get()
                + ", last="
                + lastRuntimeTaskRestore;
    }

    private static final class RemainingMarketProgress {
        private final boolean known;
        private final List<MarketAPI> markets;

        private RemainingMarketProgress(boolean known, List<MarketAPI> markets) {
            this.known = known;
            this.markets = markets;
        }

        private static RemainingMarketProgress known(List<MarketAPI> markets) {
            return new RemainingMarketProgress(true, markets);
        }

        private static RemainingMarketProgress unknown() {
            return new RemainingMarketProgress(false, List.of());
        }
    }

    /** Isolated diagnostics for save-success, save-failure and nested-save tests. */
    public static final class RuntimeTaskSaveReport {
        public final String action;
        public final int suspensionDepth;
        public final boolean graphDetached;
        public final boolean graphRestored;
        public final int taskCount;
        public final String restartPhase;

        private RuntimeTaskSaveReport(
                String action,
                int suspensionDepth,
                boolean graphDetached,
                boolean graphRestored,
                int taskCount,
                String restartPhase) {
            this.action = action;
            this.suspensionDepth = suspensionDepth;
            this.graphDetached = graphDetached;
            this.graphRestored = graphRestored;
            this.taskCount = taskCount;
            this.restartPhase = restartPhase;
        }

        private static RuntimeTaskSaveReport nested(
                String action, int depth, List<MultiFrameTask> suspendedTasks) {
            return new RuntimeTaskSaveReport(
                    action,
                    depth,
                    false,
                    false,
                    suspendedTasks == null ? 0 : suspendedTasks.size(),
                    "nested");
        }
    }

    /** Immutable diagnostics returned to the load boundary and isolated compatibility tests. */
    public static final class RuntimeTaskRestartReport {
        public final int discardedTasks;
        public final String discardedHeadTask;
        public final boolean loadGuardWasActive;
        public final boolean iterationRestarted;
        public final String restartPhase;
        public final int remainingMarketCount;
        public final boolean remainingMarketProgressKnown;
        public final boolean unknownStage;
        public final boolean stageHadStarted;
        public final String plannedStages;
        public final String mainRestartMode;
        public final State preservedState;
        public final float preservedElapsed;
        public final float preservedUntilNext;
        public final int preservedIterationsLeft;
        public final int preservedMonth;
        public final boolean preservedMonthEndRefreshPending;
        public final int preservedPreviousMonth;
        public final long campaignEpoch;
        public final long economyEpoch;

        private RuntimeTaskRestartReport(
                int discardedTasks,
                String discardedHeadTask,
                boolean loadGuardWasActive,
                boolean iterationRestarted,
                String restartPhase,
                int remainingMarketCount,
                boolean remainingMarketProgressKnown,
                boolean unknownStage,
                boolean stageHadStarted,
                String plannedStages,
                String mainRestartMode,
                State preservedState,
                float preservedElapsed,
                float preservedUntilNext,
                int preservedIterationsLeft,
                int preservedMonth,
                boolean preservedMonthEndRefreshPending,
                int preservedPreviousMonth,
                long campaignEpoch,
                long economyEpoch) {
            this.discardedTasks = discardedTasks;
            this.discardedHeadTask = discardedHeadTask;
            this.loadGuardWasActive = loadGuardWasActive;
            this.iterationRestarted = iterationRestarted;
            this.restartPhase = restartPhase;
            this.remainingMarketCount = remainingMarketCount;
            this.remainingMarketProgressKnown = remainingMarketProgressKnown;
            this.unknownStage = unknownStage;
            this.stageHadStarted = stageHadStarted;
            this.plannedStages = plannedStages;
            this.mainRestartMode = mainRestartMode;
            this.preservedState = preservedState;
            this.preservedElapsed = preservedElapsed;
            this.preservedUntilNext = preservedUntilNext;
            this.preservedIterationsLeft = preservedIterationsLeft;
            this.preservedMonth = preservedMonth;
            this.preservedMonthEndRefreshPending = preservedMonthEndRefreshPending;
            this.preservedPreviousMonth = preservedPreviousMonth;
            this.campaignEpoch = campaignEpoch;
            this.economyEpoch = economyEpoch;
        }

        public String summary() {
            return "discardedTasks="
                    + discardedTasks
                    + ", head="
                    + (discardedHeadTask == null ? "none" : discardedHeadTask)
                    + ", loadGuard="
                    + loadGuardWasActive
                    + ", restartIteration="
                    + iterationRestarted
                    + ", phase="
                    + restartPhase
                    + ", remainingMarkets="
                    + remainingMarketCount
                    + ", progressKnown="
                    + remainingMarketProgressKnown
                    + ", unknownStage="
                    + unknownStage
                    + ", stageStarted="
                    + stageHadStarted
                    + ", plannedStages="
                    + plannedStages
                    + ", mainMode="
                    + mainRestartMode
                    + ", state="
                    + preservedState
                    + ", elapsed="
                    + preservedElapsed
                    + ", untilNext="
                    + preservedUntilNext
                    + ", iterationsLeft="
                    + preservedIterationsLeft
                    + ", month="
                    + preservedMonth
                    + ", monthEndPending="
                    + preservedMonthEndRefreshPending
                    + ", previousMonth="
                    + preservedPreviousMonth
                    + ", campaignEpoch="
                    + campaignEpoch
                    + ", economyEpoch="
                    + economyEpoch;
        }
    }

    public final float getNumDaysInCurrMonth() {
        return Global.getSector().getClock().getCal().getActualMaximum(5);
    }
}
