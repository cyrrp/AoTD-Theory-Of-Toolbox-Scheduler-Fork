package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.UpdateMarketsAgainTask;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
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

    AoTDUpdateMarketAgainTask(Economy economy, List<MarketAPI> markets) {
        super(economy);
        this.markets = new ArrayList<>(markets == null ? List.of() : markets);
        this.singleMarket = null;
    }

    /** Semantic progress only; excludes already-finalized markets from a post-load restart. */
    List<MarketAPI> remainingMarketsForRuntimeRestart() {
        if (done) return List.of();
        if (singleMarket != null) return List.of(singleMarket);
        if (markets == null || marketIndex >= markets.size()) return List.of();
        return new ArrayList<>(markets.subList(Math.max(0, marketIndex), markets.size()));
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
        if (MarketRegistry.getRegistryLifecycle() != MarketRegistry.RegistryLifecycle.READY) {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.skipped-registry-not-ready", market);
            return;
        }
        long started = System.nanoTime();
        boolean registryDirty = MarketRegistry.needsDerivedRefresh(market);
        if (!registryDirty) {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.skipped-current-market", market);
            return;
        }
        if (MarketRegistry.isQuarantined(market)) {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.skipped-quarantined", market);
            return;
        }

        boolean materializedRefresh = MarketRegistry.needsMaterializedReconciliation(market);
        if (!materializedRefresh) {
            // Price/stockpile/accessibility/trade debt has no industry-state work for this task.
            // In particular, the committed-net post-immigration fast path queues only downstream
            // price work; avoid turning that O(1) proof into another industry traversal.
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.skipped-no-materialized-work", market);
            return;
        }
        final AoTDIndustryData data = AoTDIndustryData.getInstance(market);
        boolean materializedCheckpointCurrent = hasCurrentMaterializedCheckpoint(market);
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

        ArrayList<String> reconciledIndustryIds = new ArrayList<>();
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
                reconciledIndustryIds.add(industryId);
            }
        }

        boolean transitionChanged =
                conditionsReapplied || !reconciledIndustryIds.isEmpty() || desiredStateChanged;
        boolean authoritativeRefreshReady = true;
        if (transitionChanged || (materializedRefresh && !materializedCheckpointCurrent)) {
            authoritativeRefreshReady = refreshAuthoritativeSupplyDemand(market);
        } else {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.reconciliation-skipped-unchanged", market);
        }

        // Applying/unapplying industries and publishing supply/demand are one logical market
        // transition. Keep desired transitions pending when BaseIndustry maps are temporarily not
        // ready so the next scheduler pass repeats the idempotent materialization and snapshot.
        if (authoritativeRefreshReady) {
            for (String industryId : reconciledIndustryIds) {
                data.markReconciled(industryId);
            }
        }

        long elapsed = Math.max(0L, System.nanoTime() - started);
        if (authoritativeRefreshReady && (materializedRefresh || transitionChanged)) {
            long expectedInputGeneration =
                    MarketRegistry.getMarketMaterializedInputGeneration(market);
            int expectedMarketSize = market.getSize();
            if (expectedInputGeneration > 0L) {
                MarketRegistry.CommitStatus status =
                        MarketRegistry.commitMaterializedStateDetailed(
                                market, expectedInputGeneration, expectedMarketSize, elapsed);
                if (status != MarketRegistry.CommitStatus.COMMITTED) {
                    AoTDEconomySemanticBaseline.operation(
                            "update-market-again.materialized-commit-stale", market);
                }
            } else {
                AoTDEconomySemanticBaseline.operation(
                        "update-market-again.materialized-proof-missing", market);
            }
        } else if (!authoritativeRefreshReady) {
            AoTDEconomySemanticBaseline.operation(
                    "update-market-again.materialized-not-ready", market);
        }

        // Trade inputs are captured only after ImmigrationTask. Publishing here
        // would expose a pre-growth snapshot to the same iteration's global cut.
        AoTDEconomySemanticBaseline.operation(
                "update-market-again.trade-snapshot-deferred-post-immigration", market);
    }

    private static boolean hasCurrentMaterializedCheckpoint(MarketAPI market) {
        long generation = MarketRegistry.getMarketMaterializedInputGeneration(market);
        return generation > 0L
                && MarketRegistry.matchesMaterializedCheckpoint(
                        market, generation, market.getSize());
    }

    private static boolean refreshAuthoritativeSupplyDemand(MarketAPI market) {
        try (AoTDEconomySemanticBaseline.Scope ignored =
                AoTDEconomySemanticBaseline.begin(
                        "update-market-again.authoritative-supply-demand",
                        market,
                        "transition-refresh")) {
            ArrayList<AoTDSupplyDemandData> owners = new ArrayList<>();
            ArrayList<AoTDSupplyDemandData.PreparedRefresh> prepared = new ArrayList<>();
            for (CommodityOnMarketAPI commodity : new ArrayList<>(market.getAllCommodities())) {
                if (commodity instanceof AoTDCommodityOnMarket aotdCommodity) {
                    AoTDSupplyDemandData owner = aotdCommodity.getSupplyDemandDataWithoutRefresh();
                    AoTDSupplyDemandData.PreparedRefresh refresh;
                    try {
                        refresh = owner.prepareSupplyDemandData(market, true);
                    } catch (RuntimeException failure) {
                        discardPrepared(owners, prepared);
                        Global.getLogger(AoTDUpdateMarketAgainTask.class)
                                .error(
                                        "AoTD transition supply/demand refresh failed for market "
                                                + market.getId()
                                                + "; preserving the previous committed market revision.",
                                        failure);
                        MarketRegistry.quarantineMarket(
                                market, "transition-supply-demand:" + failure.getClass().getName());
                        return false;
                    }
                    if (refresh.isNotReady()) {
                        discardPrepared(owners, prepared);
                        return false;
                    }
                    owners.add(owner);
                    prepared.add(refresh);
                }
            }

            for (int i = 0; i < prepared.size(); i++) {
                owners.get(i).commitPreparedRefresh(prepared.get(i));
            }
            for (int i = 0; i < prepared.size(); i++) {
                owners.get(i).finishPreparedRefresh(prepared.get(i));
            }
            MarketRegistry.recordMaterializedCheckpoint(market, market.getSize());
            return true;
        }
    }

    private static void discardPrepared(
            List<AoTDSupplyDemandData> owners,
            List<AoTDSupplyDemandData.PreparedRefresh> prepared) {
        for (int i = 0; i < prepared.size(); i++) {
            owners.get(i).discardPreparedRefresh(prepared.get(i));
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
