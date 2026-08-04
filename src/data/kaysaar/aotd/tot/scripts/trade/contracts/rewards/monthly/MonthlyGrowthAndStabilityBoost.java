package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.conditions.AoTDToolboxCivContractPopBoost;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.CivilianSupplyProgram;
import java.awt.*;

public class MonthlyGrowthAndStabilityBoost implements TradeContractRewardDataAPI {
    AoTDTradeContract tiedContract;

    public MonthlyGrowthAndStabilityBoost(AoTDTradeContract tiedContract) {
        this.tiedContract = tiedContract;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        tooltip.addPara(
                "Provides empire-wide civilian benefits based on the commodity supplied under this contract.",
                3f);

        tooltip.addPara(
                "Supplying %s increases stability across all player colonies for each fully satisfied cycle of total faction demand.",
                3f, Color.ORANGE, "food");

        tooltip.addPara(
                "Supplying %s increases colony growth across all player colonies based on the share of total faction demand fulfilled.",
                3f, Color.ORANGE, "domestic goods");

        tooltip.addPara(
                "*These effects are applied monthly and scale with the amount delivered.*",
                Misc.getGrayColor(),
                3f);
    }

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {}

    @Override
    public void executeRewardAtTheEndOfContract() {}

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {
        for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
            marketAPI.getStability().unmodifyFlat("aotd_contract_civ");
            AoTDToolboxCivContractPopBoost.getPluginInstance(marketAPI).setCurrWeight(0);
        }
    }

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {
        int demand =
                AoTDSectorProductionDemandDataUtils.getTotalDemandFromFactionIgnoreContracts(
                        commodityId, Factions.PLAYER);
        float fulfilledDemandCycles = amountOfCommoditiesDeliveredThisMonth / (float) demand;
        if (commodityId.equals(Commodities.FOOD) && fulfilledDemandCycles >= 1) {
            int stab = (int) Math.floor(fulfilledDemandCycles);
            for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
                marketAPI
                        .getStability()
                        .modifyFlat(
                                "aotd_contract_civ",
                                stab,
                                tiedContract.getSubTypeOfContractString());
            }
        }
        if (commodityId.equals(Commodities.DOMESTIC_GOODS)) {
            for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
                AoTDToolboxCivContractPopBoost.getPluginInstance(marketAPI)
                        .setCurrWeight(
                                Math.round(
                                        fulfilledDemandCycles
                                                * CivilianSupplyProgram
                                                        .growthPerMetDemandDomesticGoods));
            }
        }
    }
}
