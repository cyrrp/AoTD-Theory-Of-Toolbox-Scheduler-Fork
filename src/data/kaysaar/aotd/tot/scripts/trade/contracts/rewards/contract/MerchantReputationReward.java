package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;

public class MerchantReputationReward implements TradeContractRewardDataAPI {
    public int amountXPIncrease;

    public MerchantReputationReward(int amountXPIncrease) {
        this.amountXPIncrease = amountXPIncrease;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        tooltip.addPara(
                "Increase merchant renown by %s",
                defaultOpadText, Color.ORANGE, "" + amountXPIncrease);
    }

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        tooltip.addPara(
                "Decrease merchant renown by %s",
                defaultOpadText,
                Misc.getNegativeHighlightColor(),
                "" + Math.round(amountXPIncrease / 2f));
    }

    @Override
    public void executeRewardAtTheEndOfContract() {
        AoTDTradeContractManager.getInstance().getCurrLevelData().addExp(amountXPIncrease);
    }

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {
        AoTDTradeContractManager.getInstance()
                .getCurrLevelData()
                .removeXp(Math.round(amountXPIncrease / 2f));
    }

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {}
}
