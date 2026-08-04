package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import java.util.*;

public class AoTDLocalResourcesSubmarketPlugin extends LocalResourcesSubmarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        if (com instanceof AoTDCommodityOnMarket commodity) {
            int limit = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
            String cid = com.getId();
            if (stockpilingBonus.containsKey(cid)) {
                limit +=
                        AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(
                                (int) stockpilingBonus.get(cid).getModifiedValue(), true, cid);
            }
            // limit *= com.getMarket().getStockpileMult().getModifiedValue();
            limit *= STOCKPILE_MAX_MONTHS;
            int deficitCountered = (int) getDeficitCountered(commodity);

            if (commodity.getExcDefData().getDeficit() - deficitCountered > 0) return 0;
            if (limit < 0) limit = 0;
            return limit;

        } else {
            return super.getStockpileLimit(com);
        }
    }

    @Override
    public int getEstimatedShortageCounteringCostPerMonth() {
        List<CommodityOnMarketAPI> all =
                new ArrayList<CommodityOnMarketAPI>(market.getAllCommodities());

        float totalCost = 0f;

        CargoAPI cargo = getCargo();

        for (CommodityOnMarketAPI commodity : all) {
            if (commodity instanceof AoTDCommodityOnMarket com) {
                float units = getDeficitCountered(com);
                if (units > 0) {
                    float per =
                            LocalResourcesSubmarketPlugin.getStockpilingUnitPrice(
                                    com.getSpec(), true);
                    totalCost += units * per;
                }
            }
        }
        return (int) totalCost;
    }

    public float getDeficitCountered(AoTDCommodityOnMarket commodity) {
        float countered = 0;
        for (Map.Entry<String, MutableStat.StatMod> entry :
                commodity.getExcDefData().deficit.getFlatMods().entrySet()) {
            if (entry.getKey().contains("aotd_shortage_counter")) {
                countered += Math.abs(entry.getValue().value);
            }
        }
        return countered;
    }

    @Override
    protected boolean doShortageCountering(
            CommodityOnMarketAPI com, float amount, boolean withShortageCountering) {
        if (com instanceof AoTDCommodityOnMarket commodity) {

            float curr = cargo.getCommodityQuantity(commodity.getId());
            float drawAmount = Math.min(curr, commodity.getDeficitQuantity());
            if (drawAmount > 0 && withShortageCountering && curr > 0) {
                float free = left.getCommodityQuantity(com.getId());
                free = Math.min(drawAmount, free);
                left.removeCommodity(com.getId(), free);
                cargo.removeCommodity(com.getId(), drawAmount);
                commodity
                        .getExcDefData()
                        .setDeficit(
                                (int) -drawAmount,
                                commodity,
                                30,
                                "aotd_shortage_counter_" + Misc.genUID());

                drawAmount -= free;
                if (market.isPlayerOwned() && drawAmount > 0) {
                    MonthlyReport report = SharedData.getData().getCurrentReport();
                    MonthlyReport.FDNode node = report.getCounterShortageNode(market);

                    CargoAPI tooltipCargo = (CargoAPI) node.custom2;
                    float addToTooltipCargo = drawAmount;
                    float q = tooltipCargo.getCommodityQuantity(com.getId()) + addToTooltipCargo;
                    if (q < 1) {
                        addToTooltipCargo = 1f; // add at least 1 unit or it won't do anything
                    }
                    tooltipCargo.addCommodity(com.getId(), addToTooltipCargo);

                    float unitPrice = (int) getStockpilingUnitPrice(commodity.getSpec(), true);
                    // node.upkeep += unitPrice * addAmount;

                    MonthlyReport.FDNode comNode = report.getNode(node, com.getId());

                    CommoditySpecAPI spec = com.getCommodity();
                    comNode.icon = spec.getIconName();
                    comNode.upkeep += unitPrice * drawAmount;
                    comNode.custom = com;

                    if (comNode.custom2 == null) {
                        comNode.custom2 = 0f;
                    }
                    comNode.custom2 = (Float) comNode.custom2 + drawAmount;

                    float qty = Math.max(1, (Float) comNode.custom2);
                    qty = (float) Math.ceil(qty);
                    comNode.name = spec.getName() + " " + Strings.X + Misc.getWithDGS(qty);
                    comNode.tooltipCreator = report.getMonthlyReportTooltip();
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        AoTDLocalResourcesTooltipSnapshot.render(this, tooltip, this::getStockpileLimitForTooltip);
    }

    private int getStockpileLimitForTooltip(CommodityOnMarketAPI commodity) {
        if (commodity instanceof AoTDCommodityOnMarket aoTDCommodity) {
            Integer snapshot =
                    AoTDLocalResourcesTooltipSnapshot.peekAoTDStockpileLimit(
                            aoTDCommodity, stockpilingBonus);
            if (snapshot != null) return snapshot;
        }
        return getStockpileLimit(commodity);
    }
}
