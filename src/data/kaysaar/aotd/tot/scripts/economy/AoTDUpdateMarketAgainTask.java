package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.UpdateMarketsAgainTask;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import java.util.ArrayList;
import java.util.List;

/**
 * Finalizes one market's authoritative AoTD state after the price phase.
 *
 * <p>The optimized path removes the unconditional second reconciliation pass. Industry
 * apply/unapply is now executed only for pending/active transitions; a full local supply/demand
 * refresh is performed only when such a transition changed the materialized industry state.
 */
public class AoTDUpdateMarketAgainTask extends UpdateMarketsAgainTask {

    public static final String INITIAL_STAGE_DESC = "AoTD economy initial stage";
    private static final int REDUCTION = 10000;

    private List<MarketAPI> markets;
    private final MarketAPI singleMarket;

    private int marketIndex = 0;
    private boolean done = false;

    public AoTDUpdateMarketAgainTask(Economy economy) {
        super(economy);
        this.markets = new ArrayList<>(economy.getMarkets());
        this.singleMarket = null;
    }

    public AoTDUpdateMarketAgainTask(Economy economy, MarketAPI singleMarket) {
        super(economy);
        this.markets = null;
        this.singleMarket = singleMarket;
    }

    @Override
    public void doNextBatch() {
        if (isDone()) return;

        if (singleMarket != null) {
            processMarket(singleMarket);
            done = true;
            return;
        } else if (markets == null) {
            markets = Global.getSector().getEconomy().getMarketsCopy();
        }

        if (marketIndex >= markets.size()) {
            done = true;
            return;
        }

        processMarket(markets.get(marketIndex));
        marketIndex++;
        if (marketIndex >= markets.size()) done = true;
    }

    private static void processMarket(MarketAPI market) {
        if (market == null) return;
        long started = System.nanoTime();
        final AoTDIndustryData data = AoTDIndustryData.getInstance(market);
        boolean registryDirty = MarketRegistry.needsDerivedRefresh(market);
        if (!registryDirty) {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.skipped-current-market", market);
            return;
        }

        boolean materializedRefresh = MarketRegistry.needsMaterializedReconciliation(market);
        boolean desiredStateChanged = false;
        if (materializedRefresh) {
            try (AoTDEconomySemanticBaseline.Scope ignored =
                    AoTDEconomySemanticBaseline.beginMarketMutation(
                            "update-market-again.detect-industry-state",
                            market,
                            "authoritative-state-refresh")) {
                AoTDEconomySemanticBaseline.operation("industry-data.check-new-industries", market);
                desiredStateChanged = data.checkForNewIndustriesAndReport(market);
            }
        }

        List<Industry> industries = new ArrayList<>(market.getIndustries());
        boolean conditionsReapplied = false;
        for (Industry industry : industries) {
            if (data.needsReconciliation(industry.getId())) {
                try (AoTDEconomySemanticBaseline.Scope ignored =
                        AoTDEconomySemanticBaseline.beginMarketMutation(
                                "update-market-again.reapply-conditions",
                                market,
                                "transition-only")) {
                    AoTDEconomySemanticBaseline.operation(
                            "market.reapplyConditions.transition-only", market);
                    market.reapplyConditions();
                }
                conditionsReapplied = true;

                // Conditions supplied by other mods may add or remove industries.
                // Refresh both the desired-state map and the traversal snapshot
                // after the callback so its live ArrayList is never iterated while
                // it is being structurally modified.
                desiredStateChanged |= data.checkForNewIndustriesAndReport(market);
                industries = new ArrayList<>(market.getIndustries());
                break;
            }
        }

        int reconciled = 0;
        for (Industry industry : industries) {
            String industryId = industry.getId();
            if (!data.needsReconciliation(industryId)) continue;

            try (AoTDEconomySemanticBaseline.Scope ignored =
                    AoTDEconomySemanticBaseline.beginMarketMutation(
                            "update-market-again.reconcile-industry", market, industryId)) {
                if (data.isPending(industryId)) {
                    AoTDEconomySemanticBaseline.operation("industry.pending-suppression", market);
                    applyPendingIndustrySuppression(industry);
                } else {
                    AoTDEconomySemanticBaseline.operation("industry.restore-active", market);
                    restoreIndustry(industry);
                }
                data.markReconciled(industryId);
                reconciled++;
            }
        }

        if (conditionsReapplied || reconciled > 0 || desiredStateChanged) {
            refreshAuthoritativeSupplyDemand(market);
        } else {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.reconciliation-skipped-unchanged", market);
        }

        long elapsed = Math.max(0L, System.nanoTime() - started);
        if (materializedRefresh || conditionsReapplied || desiredStateChanged || reconciled > 0) {
            MarketRegistry.commitMaterializedState(market, elapsed);
        }

        // Trade inputs are captured only after ImmigrationTask. Publishing here
        // would expose a pre-growth snapshot to the same iteration's global cut.
        AoTDEconomySemanticBaseline.operation(
                "update-market-again.trade-snapshot-deferred-post-immigration", market);
    }

    private static void refreshAuthoritativeSupplyDemand(MarketAPI market) {
        try (AoTDEconomySemanticBaseline.Scope ignored =
                AoTDEconomySemanticBaseline.begin(
                        "update-market-again.authoritative-supply-demand",
                        market,
                        "transition-refresh")) {
            for (CommodityOnMarketAPI commodity : new ArrayList<>(market.getAllCommodities())) {
                if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                    aotdCommodity.getSupplyDemandData().updateSupplyDemandData(market, true);
                }
            }
        }
    }

    public static void applyPendingIndustrySuppression(Industry industry) {
        industry.getSupplyBonusFromOther()
                .modifyFlat(AoTDIndustryData.source, -getReduction(), INITIAL_STAGE_DESC);
        industry.getDemandReductionFromOther()
                .modifyFlat(AoTDIndustryData.source, getReduction(), INITIAL_STAGE_DESC);

        AoTDEconomySemanticBaseline.operation("industry.apply.pending", 1L);
        industry.apply();
        AoTDEconomySemanticBaseline.operation("industry.unapply.pending", 1L);
        industry.unapply();
    }

    private static void restoreIndustry(Industry industry) {
        industry.getSupplyBonusFromOther().unmodifyFlat(AoTDIndustryData.source);
        industry.getDemandReductionFromOther().unmodifyFlat(AoTDIndustryData.source);

        AoTDEconomySemanticBaseline.operation("industry.unapply.active", 1L);
        industry.unapply();
        AoTDEconomySemanticBaseline.operation("industry.apply.active", 1L);
        industry.apply();
    }

    public static int getReduction() {
        return REDUCTION;
    }

    @Override
    public boolean isDone() {
        return done;
    }
}
