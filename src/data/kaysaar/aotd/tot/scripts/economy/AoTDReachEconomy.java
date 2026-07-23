package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.campaign.econ.Economy;
import com.fs.starfarer.campaign.econ.reach.*;

import java.util.ArrayList;
import java.util.List;

public class AoTDReachEconomy extends ReachEconomy {
    public void nextStepForPlayer(MainWorkTask.EconWorkParams econWorkParams) {
        final List<MarketAPI> markets = this.getMarkets().stream().filter(x -> x.isPlayerOwned() || x.getFaction().isPlayerFaction()).toList();
        for (MarketAPI market : markets) {
            final PersonAPI admin = market.getAdmin();
            admin.getStats().refreshCharacterStatsEffects();
            admin.getStats().refreshGovernedOutpostEffects(market);
        }
        final AoTdMainWorkTask2 workTask = new AoTdMainWorkTask2(markets, this, econWorkParams);

        while (!workTask.isDone()) {
            workTask.doNextBatch();
            workTask.awaitWorkersIfSubmitted();
        }

        final AoTDUpdateMarketAgainTask marketUpdateTask = new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy());
        while (!marketUpdateTask.isDone()) {
            marketUpdateTask.doNextBatch();
        }
        if (econWorkParams.withImmigration) {
            final ImmigrationTask immigrationTask = new ImmigrationTask(markets, this, !econWorkParams.forceNonUIStep);

            while (!immigrationTask.isDone()) {
                immigrationTask.doNextBatch();
            }
        }

        final AoTDPostImmigrationTradeSnapshotTask tradeSnapshotTask =
                new AoTDPostImmigrationTradeSnapshotTask(markets, "player-step");
        while (!tradeSnapshotTask.isDone()) {
            tradeSnapshotTask.doNextBatch();
        }

        final AoTDFinishEconomyUpdateTask finishUpdateTask = new AoTDFinishEconomyUpdateTask((Economy) Global.getSector().getEconomy());
        finishUpdateTask.doForPlayerOnly();
    }

    @Override
    public void nextStep(MainWorkTask.EconWorkParams econWorkParams) {
        final List<MarketAPI> markets = new ArrayList<>(this.getMarkets());

        final MarketAPI openMarket = Global.getSector().getCurrentlyOpenMarket();
        if (openMarket != null) {
            final PersonAPI admin = openMarket.getAdmin();
            admin.getStats().refreshCharacterStatsEffects();
            admin.getStats().refreshGovernedOutpostEffects(openMarket);

            final AoTdMainWorkTask2 workTask = new AoTdMainWorkTask2(markets, this, econWorkParams, openMarket);

            while (!workTask.isDone()) {
                workTask.doNextBatch();
                workTask.awaitWorkersIfSubmitted();
            }

            final AoTDUpdateMarketAgainTask marketUpdateTask = new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy(), openMarket);
            while (!marketUpdateTask.isDone()) {
                marketUpdateTask.doNextBatch();
            }

        } else {
            for (MarketAPI market : markets) {
                final PersonAPI admin = market.getAdmin();
                admin.getStats().refreshCharacterStatsEffects();
                admin.getStats().refreshGovernedOutpostEffects(market);
            }

            final AoTdMainWorkTask2 workTask = new AoTdMainWorkTask2(markets, this, econWorkParams);

            while (!workTask.isDone()) {
                workTask.doNextBatch();
                workTask.awaitWorkersIfSubmitted();
            }

            final AoTDUpdateMarketAgainTask marketUpdateTask = new AoTDUpdateMarketAgainTask((Economy) Global.getSector().getEconomy());
            while (!marketUpdateTask.isDone()) {
                marketUpdateTask.doNextBatch();
            }
        }

        if (econWorkParams.withImmigration) {
            final ImmigrationTask immigrationTask = new ImmigrationTask(markets, this, !econWorkParams.forceNonUIStep);

            while (!immigrationTask.isDone()) {
                immigrationTask.doNextBatch();
            }
        }

        final AoTDPostImmigrationTradeSnapshotTask tradeSnapshotTask =
                new AoTDPostImmigrationTradeSnapshotTask(markets, "manual-step");
        while (!tradeSnapshotTask.isDone()) {
            tradeSnapshotTask.doNextBatch();
        }

        final FinishEconomyUpdateTask finishUpdateTask = new AoTDFinishEconomyUpdateTask((Economy) Global.getSector().getEconomy());
        while (!finishUpdateTask.isDone()) {
            finishUpdateTask.doNextBatch();
        }
    }

    @Override
    public void addMarket(MarketAPI marketAPI) {
        super.addMarket(marketAPI);
        //Here swap of all commodities into AoTDcommoidtyData
    }
}