package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.FinishEconomyUpdateTask;
import com.fs.starfarer.campaign.econ.reach.ImmigrationTask;
import com.fs.starfarer.campaign.econ.reach.MainWorkTask;
import com.fs.starfarer.campaign.econ.reach.ReachEconomy;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityMarketData;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class AoTDReachEconomy extends ReachEconomy {
    public void nextStepForPlayer(MainWorkTask.EconWorkParams econWorkParams) {
        econWorkParams = normalizeWorkParams(econWorkParams);
        final List<MarketAPI> markets =
                this.getMarkets().stream()
                        .filter(x -> x.isPlayerOwned() || x.getFaction().isPlayerFaction())
                        .toList();
        refreshAdministrators(markets);
        runMainTask(markets, econWorkParams, null);

        final AoTDUpdateMarketAgainTask marketUpdateTask =
                new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy());
        drain(marketUpdateTask);
        if (econWorkParams.withImmigration) {
            drain(new ImmigrationTask(markets, this, !econWorkParams.forceNonUIStep));
        }
        drain(new AoTDPostImmigrationTradeSnapshotTask(markets, "player-step"));

        final AoTDFinishEconomyUpdateTask finishUpdateTask =
                new AoTDFinishEconomyUpdateTask((Economy) Global.getSector().getEconomy());
        finishUpdateTask.doForPlayerOnly();
    }

    /**
     * Synchronous UI refresh for exactly one market.
     *
     * <p>This path intentionally does not rebuild global commodity-market data or settle global
     * internal trade. It publishes a complete local market revision, runs immigration and its trade
     * snapshot only for that market, then performs one local follow-up when the snapshot dirtied
     * price/stockpile outputs.
     */
    final void nextStepForUiMarket(
            MainWorkTask.EconWorkParams econWorkParams, MarketAPI market, String context) {
        if (market == null) return;
        econWorkParams = normalizeWorkParams(econWorkParams);
        final ArrayList<MarketAPI> localMarkets = new ArrayList<>(1);
        localMarkets.add(market);
        refreshAdministrator(market);

        runMainTask(localMarkets, econWorkParams, market);
        drain(new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(), market));

        if (econWorkParams.withImmigration) {
            drain(new ImmigrationTask(localMarkets, this, !econWorkParams.forceNonUIStep));
        }

        // The old implementation accidentally passed every market here even in
        // the single-market branch. Keep the atomic publication contract, but
        // limit capture to the market whose UI is being opened.
        drain(
                new AoTDPostImmigrationTradeSnapshotTask(
                        localMarkets, "ui-" + (context == null ? "market" : context)));

        // Immigration/net-production publication may create a fresh local price
        // revision. Finish it before the UI observes the market and before the
        // duplicate Cargo tripleStep is eligible for coalescing.
        if (MarketRegistry.needsMaterializedReconciliation(market)
                || MarketRegistry.needsPriceRefresh(market)) {
            runMainTask(localMarkets, econWorkParams, market);
            drain(new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(), market));
        }

        // Preserve the observable Economy.nextStep listener boundary while
        // leaving global internal-trade settlement on the real economy cadence.
        AoTDFinishEconomyUpdateTask.notifyEconomyListenersOnly(
                (Economy) Global.getSector().getEconomy(),
                "ui-" + (context == null ? "market" : context));
    }

    /**
     * UI mutation refresh: materialize one market, rebuild global/econ-group data only for the
     * sorted affected IDs, then publish the local price/snapshot revision and listener boundary.
     */
    final void nextStepForUiMarketMutation(
            MainWorkTask.EconWorkParams econWorkParams,
            MarketAPI market,
            String[] affectedCommodityIds,
            int scope,
            String context) {
        if (market == null || affectedCommodityIds == null || affectedCommodityIds.length == 0)
            return;
        econWorkParams = normalizeWorkParams(econWorkParams);
        final ArrayList<MarketAPI> localMarkets = new ArrayList<>(1);
        localMarkets.add(market);
        refreshAdministrator(market);

        // Fork-native local materialization retains the exact AoTD industry and
        // pure-price commit semantics while limiting live market work to one market.
        runMainTask(localMarkets, econWorkParams, market, false);
        drain(new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(), market));

        if ((scope & SchedulerBridge.REFRESH_IMMIGRATION) != 0 && econWorkParams.withImmigration) {
            drain(new ImmigrationTask(localMarkets, this, !econWorkParams.forceNonUIStep));
        }
        drain(
                new AoTDPostImmigrationTradeSnapshotTask(
                        localMarkets, "targeted-ui-" + (context == null ? "market" : context)));

        if (MarketRegistry.needsMaterializedReconciliation(market)
                || MarketRegistry.needsPriceRefresh(market)) {
            runMainTask(localMarkets, econWorkParams, market, false);
            drain(new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(), market));
        }

        rebuildAffectedCommodityData(affectedCommodityIds);
        notifyAffectedCommodityListeners(affectedCommodityIds);
        AoTDFinishEconomyUpdateTask.notifyEconomyListenersOnly(
                (Economy) Global.getSector().getEconomy(),
                "targeted-ui-" + (context == null ? "market" : context));
    }

    private static void notifyAffectedCommodityListeners(String[] ids) {
        Economy economy = (Economy) Global.getSector().getEconomy();
        ArrayList<EconomyAPI.EconomyUpdateListener> activeListeners = new ArrayList<>();
        for (EconomyAPI.EconomyUpdateListener listener :
                new ArrayList<>(economy.getUpdateListeners())) {
            if (listener == null) continue;
            if (listener.isEconomyListenerExpired()) {
                economy.removeUpdateListener(listener);
            } else {
                activeListeners.add(listener);
            }
        }
        for (String id : new TreeSet<>(java.util.Arrays.asList(ids))) {
            if (id == null || id.isBlank()) continue;
            for (EconomyAPI.EconomyUpdateListener listener : activeListeners) {
                listener.commodityUpdated(id);
            }
        }
    }

    private void rebuildAffectedCommodityData(String[] affectedCommodityIds) {
        TreeSet<String> ids = new TreeSet<>();
        for (String id : affectedCommodityIds) {
            if (id != null && !id.isBlank()) ids.add(id);
        }
        TreeSet<String> groups = new TreeSet<>();
        for (MarketAPI candidate : getMarkets()) {
            if (candidate == null) continue;
            String group = candidate.getEconGroup();
            if (group != null && !group.isBlank()) groups.add(group);
        }
        for (String id : ids) {
            new AoTDCommodityMarketData(id, null);
            for (String group : groups) {
                new AoTDCommodityMarketData(id, group);
            }
        }
    }

    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        nextStepGlobally(econWorkParams);
    }

    /** Full all-market economy step; it never consults currentlyOpenMarket. */
    private void nextStepGlobally(MainWorkTask.EconWorkParams econWorkParams) {
        econWorkParams = normalizeWorkParams(econWorkParams);
        final List<MarketAPI> markets = new ArrayList<>(this.getMarkets());
        refreshAdministrators(markets);
        runMainTask(markets, econWorkParams, null);

        final AoTDUpdateMarketAgainTask marketUpdateTask =
                new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy());
        drain(marketUpdateTask);

        if (econWorkParams.withImmigration) {
            drain(new ImmigrationTask(markets, this, !econWorkParams.forceNonUIStep));
        }

        drain(new AoTDPostImmigrationTradeSnapshotTask(markets, "manual-step"));

        final FinishEconomyUpdateTask finishUpdateTask =
                new AoTDFinishEconomyUpdateTask((Economy) Global.getSector().getEconomy());
        drain(finishUpdateTask);
    }

    private void runMainTask(
            List<MarketAPI> markets, MainWorkTask.EconWorkParams params, MarketAPI singleMarket) {
        runMainTask(markets, params, singleMarket, true);
    }

    private void runMainTask(
            List<MarketAPI> markets,
            MainWorkTask.EconWorkParams params,
            MarketAPI singleMarket,
            boolean notifyCommodityListeners) {
        final AoTdMainWorkTask2 task =
                singleMarket == null
                        ? new AoTdMainWorkTask2(markets, this, params)
                        : new AoTdMainWorkTask2(
                                markets, this, params, singleMarket, notifyCommodityListeners);
        while (!task.isDone()) {
            task.doNextBatch();
            task.awaitWorkersIfSubmitted();
        }
    }

    private static void refreshAdministrators(List<MarketAPI> markets) {
        for (MarketAPI market : markets) refreshAdministrator(market);
    }

    private static MainWorkTask.EconWorkParams normalizeWorkParams(
            MainWorkTask.EconWorkParams params) {
        if (params != null) return params;
        MainWorkTask.EconWorkParams normalized = new MainWorkTask.EconWorkParams();
        normalized.withIncomeAndUpkeep = false;
        normalized.withStockpileUpdate = true;
        normalized.withImmigration = true;
        return normalized;
    }

    private static void refreshAdministrator(MarketAPI market) {
        if (market == null) return;
        final PersonAPI admin = market.getAdmin();
        if (admin == null) return;
        admin.getStats().refreshCharacterStatsEffects();
        admin.getStats().refreshGovernedOutpostEffects(market);
    }

    private static void drain(com.fs.starfarer.campaign.econ.contract.iter.MultiFrameTask task) {
        while (!task.isDone()) task.doNextBatch();
    }

    @Override
    public void addMarket(MarketAPI marketAPI) {
        super.addMarket(marketAPI);
        // Here swap of all commodities into AoTDCommodityData.
    }
}
