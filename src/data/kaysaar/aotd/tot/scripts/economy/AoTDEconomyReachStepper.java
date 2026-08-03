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
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.*;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.scripts.trade.tasks.AoTDExternalTradeSolver;
import data.kaysaar.aotd.tot.ui.income.AoTDMonthlyTooltipCreator;

import java.util.ArrayList;

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

        if(report != null){
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
                             "economy.month-end-preparation", null,
                             "month-end")) {
        SectorSurplusConsumptionStats.getInstance().clear();

        for (AoTDFactionTradeData tradeData : AoTDTradeManager.getInstance().getAllFactionTradeData().values()) {
            tradeData.doEndOfMonthStuffForHistory(prevMonth);
        }

        if (!AoTDEconomy.runningPrePlayerEconomy) {

            // NEW: contracts phase (after internal, before external)
            AoTDTradeContractManager.getInstance().runMonthlyContracts();

            // Build external index from remainingNet (post-internal, post-contract)
            final AoTDSectorExternalIndex idx = new AoTDSectorExternalIndex();
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                final AoTDFactionTradeData f = AoTDTradeManager.getInstance().getFactionTradeData(market.getFactionId());
                final AoTDMarketData md = f.getTradeData().get(market.getId());
                if (md == null) continue;

                md.resetExternalResults(); // excess-exported tracking for this month
                if(!market.hasSpaceport()||market.getAccessibilityMod().computeEffective(0f)<=0f)continue;
                idx.addMarket(market, md);
            }

            new AoTDExternalTradeSolver().runMonthEndExternalTrade(idx);

            // 5) Apply leftover deficit/excess to AoTDExcDefData
            final float durDays = 31;

            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDFactionTradeData f = AoTDTradeManager.getInstance().getFactionTradeData(market.getFactionId());

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
        elapsed += delta;
        ContractEconomy.DEBUG = false;

        if (state == ReachEconomyStepper.State.WAITING) {
            final int month = Global.getSector().getClock().getMonth();
            if (month != prevMonth) {
                pendingPreviousMonth = this.prevMonth;
                this.prevMonth = month;
                iterLeft = Economy.NUM_ITER_PER_MONTH;

                final float daysInThisMonth = getNumDaysInCurrMonth()
                        - Global.getSector().getClock().getDay();
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
                            baselineRevision,
                            "iteration-complete");
                    baselineRevision = 0L;
                }
                state = ReachEconomyStepper.State.WAITING;
            }
        }
    }

    private void createTasks() {
        tasks = new ArrayList<>();
        baselineRevision = AoTDEconomySemanticBaseline.beginEconomyRevision(
                "reach-stepper-iteration");
        final boolean iterationsDone = iterLeft <= 0 || monthEndRefreshPending;
        final MainWorkTask.EconWorkParams mainWork = new MainWorkTask.EconWorkParams();
        mainWork.withIncomeAndUpkeep = true;
        mainWork.withStockpileUpdate = iterationsDone;

        final Economy mainEcon = (Economy) Global.getSector().getEconomy();
        tasks.add(new AoTdMainWorkTask2(econ.getMarkets(), econ, mainWork));
        tasks.add(new AoTDUpdateMarketAgainTask(mainEcon));
        tasks.add(new ImmigrationTask(econ.getMarkets(), econ, false));
        tasks.add(new AoTDPostImmigrationTradeSnapshotTask(
                econ.getMarkets(), monthEndRefreshPending ? "month-end-refresh" : "regular-iteration"));
        tasks.add(new AoTDFinishEconomyUpdateTask(mainEcon));
        AoTDEconomySemanticBaseline.operation("economy.task.main-work", 1L);
        AoTDEconomySemanticBaseline.operation("economy.task.update-market-again", 1L);
        AoTDEconomySemanticBaseline.operation("economy.task.immigration", 1L);
        AoTDEconomySemanticBaseline.operation(
                "economy.task.post-immigration-trade-snapshot", 1L);
        AoTDEconomySemanticBaseline.operation("economy.task.finish-global-cut", 1L);
    }

    public final float getNumDaysInCurrMonth() {
        return Global.getSector().getClock().getCal().getActualMaximum(5);
    }
}
