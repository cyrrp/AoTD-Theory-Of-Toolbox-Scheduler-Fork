package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanel;
import data.kaysaar.aotd.tot.ui.industry.IndustryOnHoverTooltipV2;

import java.util.ArrayList;
import java.util.List;

import static data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask.INITIAL_STAGE_DESC;
import static data.kaysaar.aotd.tot.scripts.economy.AoTDUpdateMarketAgainTask.getReduction;
import static data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanel.height;

public class AoTDToobloxIndustryListener implements IndustryOptionProvider {
    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        return List.of();
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {

    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {

    }

    @Override
    public void addToIndustryTooltip(Industry ind, Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {

        if(AoTDIndustryData.getInstance(ind.getMarket()).isPending(ind.getId())){
            tooltip.addSectionHeading("Preparations",ind.getMarket().getFaction().getBaseUIColor(),ind.getMarket().getFaction().getDarkUIColor(), Alignment.MID,5f);
            tooltip.addPara("This industry is currently in-effective, as it needs resources to be allocated, it will be activated in next month!", Misc.getTooltipTitleAndLightHighlightColor(),5f).setAlignment(Alignment.MID);
            MarketAPI newM = ind.getMarket().clone();

            for (CommodityOnMarketAPI allCommodity : newM.getAllCommodities()) {
                allCommodity.getAvailableStat().setBaseValue(20000);
            }
            Industry newINdustry = newM.getIndustry(ind.getId());
            float y = tooltip.getHeightSoFar();
            float currX = 0;
            float currY = y;
            int maxInRow = 3;
            float effectiveWidth = (width-6)/maxInRow;
            // Preview uses the cloned market/industry only. The live market is
            // authoritative and must not be mutated by tooltip construction.
            int currInRow = 0;
            ArrayList<CustomPanelAPI>toPlace = new ArrayList<>();
            if(!newINdustry.getAllDemand().isEmpty()){
                tooltip.addSectionHeading("Demand Necessary",ind.getMarket().getFaction().getBaseUIColor(),ind.getMarket().getFaction().getDarkUIColor(), Alignment.MID,5f);
                for (MutableCommodityQuantity entry : newINdustry.getAllDemand()) {
                    currX = 0;
                    if(currInRow<maxInRow){
                        toPlace.add(new AoTDCommodityShortPanel(entry.getCommodityId(),
                                -AoTDCommodityEconSpecManager.getEconSpec(entry.getCommodityId()).getCalculationScript().getRawUnitsFromDemand(entry.getQuantity(),newM,entry.getCommodityId(),newINdustry),Misc.getNegativeHighlightColor(),effectiveWidth).getMainPanel());
                        currInRow++;
                    }
                    else{
                        CustomPanelAPI row = Global.getSettings().createCustom(width,height,null);
                        for (CustomPanelAPI customPanelAPI : toPlace) {
                            row.addComponent(customPanelAPI).inTL(currX,0);
                            currX += effectiveWidth+2;
                        }
                        tooltip.addCustom(row,3f);
                        toPlace.clear();
                        currInRow = 0;
                        toPlace.add(new AoTDCommodityShortPanel(entry.getCommodityId(),
                                -AoTDCommodityEconSpecManager.getEconSpec(entry.getCommodityId()).getCalculationScript().getRawUnitsFromDemand(entry.getQuantity(),newM,entry.getCommodityId(),newINdustry),Misc.getNegativeHighlightColor(),effectiveWidth).getMainPanel());
                        currInRow++;
                    }
                }
                placeRows(toPlace, width, effectiveWidth, currX, tooltip);
            }
            if(!newINdustry.getAllSupply().isEmpty()){
                currInRow = 0;
                tooltip.addSectionHeading("Estimated production in next month",ind.getMarket().getFaction().getBaseUIColor(),ind.getMarket().getFaction().getDarkUIColor(), Alignment.MID,5f);
                for (MutableCommodityQuantity entry : newINdustry.getAllSupply()) {
                    currX = 0;
                    if(currInRow<maxInRow){
                        toPlace.add(new AoTDCommodityShortPanel(entry.getCommodityId(),
                                AoTDCommodityEconSpecManager.getEconSpec(entry.getCommodityId()).getCalculationScript().getRawUnitsFromSupply(entry.getQuantity(),newM,entry.getCommodityId(),newINdustry),Misc.getPositiveHighlightColor(),effectiveWidth).getMainPanel());
                        currInRow++;
                    }
                    else{
                        CustomPanelAPI row = Global.getSettings().createCustom(width,height,null);
                        for (CustomPanelAPI customPanelAPI : toPlace) {
                            row.addComponent(customPanelAPI).inTL(currX,0);
                            currX += effectiveWidth+2;
                        }
                        tooltip.addCustom(row,3f);
                        toPlace.clear();
                        currInRow = 0;
                        toPlace.add(new AoTDCommodityShortPanel(entry.getCommodityId(),
                                AoTDCommodityEconSpecManager.getEconSpec(entry.getCommodityId()).getCalculationScript().getRawUnitsFromSupply(entry.getQuantity(),newM,entry.getCommodityId(),newINdustry),Misc.getPositiveHighlightColor(),effectiveWidth).getMainPanel());
                        currInRow++;
                    }
                }
                placeRows(toPlace, width, effectiveWidth, currX, tooltip);
            }
            for (CommodityOnMarketAPI allCommodity : newM.getAllCommodities()) {
                allCommodity.getAvailableStat().setBaseValue(0);
            }

        }
}

    private static void placeRows(ArrayList<CustomPanelAPI> toPlace, float width, float effectiveWidth, float currX, TooltipMakerAPI tooltip) {
        if (!toPlace.isEmpty()) {
            CustomPanelAPI row = Global.getSettings().createCustom(width, height, null);
            int size = toPlace.size();
            float effectiveWidthTaken = size * effectiveWidth + ((size - 1) * 2);
            currX = (width / 2) - (effectiveWidthTaken / 2);
            for (CustomPanelAPI customPanelAPI : toPlace) {
                row.addComponent(customPanelAPI).inTL(currX, 0);
                currX += effectiveWidth + 2;
            }
            tooltip.addCustom(row, 3f);
            toPlace.clear();
        }
    }
}
