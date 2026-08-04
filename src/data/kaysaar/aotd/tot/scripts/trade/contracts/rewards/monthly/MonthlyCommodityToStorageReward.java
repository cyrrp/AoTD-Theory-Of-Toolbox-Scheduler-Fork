package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.monthly;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;

public class MonthlyCommodityToStorageReward implements TradeContractRewardDataAPI {
    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        MarketAPI gatheringPoint =
                Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();

        if (gatheringPoint != null) {

            tooltip.addPara(
                    "At the end of each month, delivered commodities will be transferred to local storage at %s.",
                    3f, Color.ORANGE, gatheringPoint.getName());

            tooltip.addPara(
                    "*Payment is only required for the quantity successfully delivered.",
                    Misc.getGrayColor().brighter(),
                    5f);
        }
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
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {}

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {
        MarketAPI gatheringPoint =
                Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
        if (gatheringPoint != null) {
            gatheringPoint
                    .getSubmarket(Submarkets.SUBMARKET_STORAGE)
                    .getCargo()
                    .addCommodity(commodityId, amountOfCommoditiesDeliveredThisMonth);
        }
    }
}
