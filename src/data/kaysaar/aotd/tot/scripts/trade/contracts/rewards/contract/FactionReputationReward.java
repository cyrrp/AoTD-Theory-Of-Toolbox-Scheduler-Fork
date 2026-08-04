package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.contract;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.TradeContractRewardDataAPI;
import java.awt.*;

public class FactionReputationReward implements TradeContractRewardDataAPI {
    String factionId;
    int changePlus;
    int changeMinus;

    public FactionReputationReward(String factionId, int changePlus, int changeMinus) {
        this.factionId = factionId;
        this.changePlus = changePlus;
        this.changeMinus = changeMinus;
    }

    @Override
    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        tooltip.addPara(
                "Increase reputation with %s by %s",
                defaultOpadText,
                new Color[] {faction.getBaseUIColor(), Color.ORANGE},
                AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()),
                changePlus + "");
    }

    @Override
    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        tooltip.addPara(
                "Decrease reputation with %s by %s",
                defaultOpadText,
                new Color[] {faction.getBaseUIColor(), Misc.getNegativeHighlightColor()},
                AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()),
                changeMinus + "");
    }

    @Override
    public void executeRewardAtTheEndOfContract() {}

    @Override
    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually) {}

    @Override
    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId) {}
}
