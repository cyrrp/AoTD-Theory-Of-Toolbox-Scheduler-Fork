package data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.impl.playercontracts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import java.awt.*;

public abstract class DirectedTradeInitiativeBaseContract implements PlayerContractCreatorAPI {
    public static float cutFromInternalWorth = 0.3f;

    @Override
    public void onContractCreated(AoTDTradeContract generatedContract) {
        generatedContract.setNewId(getBaseIdForContract());
    }

    @Override
    public void applyChangesToContractIfNecessary(AoTDTradeContract contract) {
        contract.runCleanUp();
    }

    @Override
    public int getMaxLimitForCommodityAmount(String commodityId) {
        return AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                        commodityId, Factions.PLAYER)
                - AoTDSectorProductionDemandDataUtils.getTotalDemandFromFactionBeforeContract(
                        commodityId, Factions.PLAYER, getBaseIdForContract());
    }

    @Override
    public float getCutToPayForCommodity(String commodityId) {
        return AoTDCommodityEconSpecManager.getCutForCommodity(commodityId, true)
                * cutFromInternalWorth;
    }

    @Override
    public int getMaxAmountOfConcurrentContracts() {
        return 1;
    }

    @Override
    public TooltipMakerAPI.TooltipCreator generateTooltipCreatorForButtonOnList(
            float widthOfTooltip) {
        return new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 600;
            }

            @Override
            public void createTooltip(
                    TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addTitle(getNameOfContract());
                createContractExplanationSection(tooltip, getTooltipWidth(tooltipParam));
                if (!canUseContract()) {
                    tooltip.addPara(
                            "To issue such contract one of these commodities production must be bigger, than faction's demand",
                            Misc.getTooltipTitleAndLightHighlightColor(),
                            3f);
                    tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
                    for (String s : getAvailableCommoditiesForContract()) {
                        String name = Global.getSettings().getCommoditySpec(s).getName();
                        tooltip.addPara(name, Color.ORANGE, 3f);
                    }
                    tooltip.setBulletedListMode(null);
                }
            }
        };
    }

    @Override
    public boolean isContractUnlimited() {
        return false;
    }

    @Override
    public boolean canEditContract() {
        return true;
    }

    @Override
    public boolean canUseContract() {
        boolean atLeastOnce = false;
        for (String s : getAvailableCommoditiesForContract()) {
            if (getMaxLimitForCommodityAmount(s) > 0) {
                atLeastOnce = true;
                break;
            }
        }
        return atLeastOnce;
    }

    @Override
    public boolean useUnits() {
        return false;
    }

    @Override
    public boolean isContractPaidByPlayer() {
        return false;
    }
}
