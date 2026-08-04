package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface TradeContractRewardDataAPI {
    public static enum TradeContractRewardTooltipMode {
        CONTRACT_DATA,
        CONTRACT_BROWSER
    }

    public void createRewardSection(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText);

    public void createPenaltySectionForNotMeetingContract(
            TooltipMakerAPI tooltip,
            float width,
            TradeContractRewardTooltipMode mode,
            float defaultOpadText);

    public void executeRewardAtTheEndOfContract();

    public void executePenaltyAthTheTerminationOfContract(boolean wasTerminatedByPlayerManually);

    public void executeRewardMonthly(
            int amountOfCommoditiesDeliveredThisMonth, int reqThisMonth, String commodityId);
}
