package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts.FuelLogisticProgram;
import java.awt.*;

public class MonthlyFuelAccessibilityBoost implements TradeContractRewardDataAPI {
    AoTDTradeContract tiedContract;

    public MonthlyFuelAccessibilityBoost(AoTDTradeContract tiedContract) {
        this.tiedContract = tiedContract;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        float percent =
                tiedContract.getMonthlyAmountNeeded(Commodities.FUEL) / FuelLogisticProgram.perFuel;
        tooltip.addPara(
                "Increases accessibility across all colonies by %s.",
                3f, Color.ORANGE, Misc.getRoundedValueMaxOneAfterDecimal(percent) + "%");
        tooltip.addPara(
                "*This bonus is applied while the contract is fully maintained. If conditions are not met, the effect is reduced.*",
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
            marketAPI.getAccessibilityMod().unmodifyFlat("aotd_fuel_contract");
        }
    }

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {
        if (commodityId.equals(Commodities.FUEL)) {
            float percentage =
                    tiedContract.getMonthlyAmountNeeded(Commodities.FUEL)
                            / FuelLogisticProgram.perFuel;
            percentage /= 100;
            for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
                marketAPI
                        .getAccessibilityMod()
                        .modifyFlat(
                                "aotd_fuel_contract",
                                percentage,
                                tiedContract.getSubTypeOfContractString());
            }
        }
    }
}
