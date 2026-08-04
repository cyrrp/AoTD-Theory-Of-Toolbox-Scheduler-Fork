package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import java.util.LinkedHashSet;

public interface PlayerContractCreatorAPI {
    public void onContractCreated(AoTDTradeContract generatedContract);

    // This method is called in two palces : before month end of when UI is created
    // You can use it for contracts that are based on amount of production you have
    public void applyChangesToContractIfNecessary(AoTDTradeContract contract);

    public String getNameOfContract();

    public String getBaseIdForContract();

    public LinkedHashSet<String> getAvailableCommoditiesForContract();

    public int getMaxLimitForCommodityAmount(String commodityId);

    public boolean canUseContract();

    public float getCutToPayForCommodity(String commodityId);

    public int getMaxAmountOfConcurrentContracts();

    public void createContractExplanationSection(TooltipMakerAPI tooltip, float width);

    public TooltipMakerAPI.TooltipCreator generateTooltipCreatorForButtonOnList(
            float widthOfTooltip);

    public boolean isContractUnlimited();

    public boolean canEditContract();

    public boolean useUnits();

    public void createProcTooltipSection(
            TooltipMakerAPI tooltip, float width, float price, int amount, String commodity);

    public boolean isContractPaidByPlayer();

    public float getYForExplainSection();
}
