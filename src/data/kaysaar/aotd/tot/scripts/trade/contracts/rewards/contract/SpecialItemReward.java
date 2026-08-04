package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;

public class SpecialItemReward implements TradeContractRewardDataAPI {
    String id;
    String data;

    public SpecialItemReward(String id, String data) {
        this.id = id;
        this.data = data;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
        tooltip.addPara("Gain: %s", defaultOpadText, Color.ORANGE, spec.getName());
    }

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {}

    @Override
    public void executeRewardAtTheEndOfContract() {
        MarketAPI market =
                Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
        if (market != null) {
            market.getSubmarket(Submarkets.SUBMARKET_STORAGE)
                    .getCargo()
                    .addSpecial(new SpecialItemData(id, data), 1);
        }
    }

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {}

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {}
}
