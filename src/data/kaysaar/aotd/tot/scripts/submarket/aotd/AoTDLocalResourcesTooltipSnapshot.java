package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds a self-consistent, call-local snapshot for the Local Resources tooltip.
 * Expensive virtual limit calculations happen once per commodity and
 * never from the sort comparator.
 */
public final class AoTDLocalResourcesTooltipSnapshot {
    private AoTDLocalResourcesTooltipSnapshot() {}

    @FunctionalInterface
    public interface LimitResolver {
        int getStockpileLimit(CommodityOnMarketAPI commodity);
    }

    private static final class Row {
        private final CommodityOnMarketAPI commodity;
        private final int rawLimit;
        private final int sourceOrder;

        private Row(CommodityOnMarketAPI commodity, int rawLimit,
                    int sourceOrder) {
            this.commodity = commodity;
            this.rawLimit = rawLimit;
            this.sourceOrder = sourceOrder;
        }
    }

    public static void render(LocalResourcesSubmarketPlugin plugin,
                              TooltipMakerAPI tooltip,
                              LimitResolver resolver) {
        List<CommodityOnMarketAPI> commodities =
                new ArrayList<>(plugin.getMarket().getAllCommodities());
        List<Row> rows = new ArrayList<>(commodities.size());
        int sourceOrder = 0;
        for (CommodityOnMarketAPI commodity : commodities) {
            rows.add(new Row(commodity,
                    resolver.getStockpileLimit(commodity), sourceOrder++));
        }

        rows.sort(Comparator
                .comparingInt((Row row) -> row.rawLimit).reversed()
                .thenComparingInt(row -> row.sourceOrder));

        float opad = 10f;
        tooltip.beginGridFlipped(400f, 1, 70f, opad);
        int rowIndex = 0;
        for (Row row : rows) {
            CommodityOnMarketAPI commodity = row.commodity;
            if (commodity.isNonEcon()) continue;
            if (commodity.getCommodity().isMeta()) continue;
            if (!plugin.shouldHaveCommodity(commodity)) continue;

            int monthlyRate = (int) Math.round(
                    row.rawLimit * plugin.getStockpilingAddRateMult(commodity));
            if (monthlyRate <= 0) continue;
            tooltip.addToGrid(0, rowIndex++,
                    commodity.getCommodity().getName(),
                    Misc.getWithDGS(monthlyRate));
        }

        tooltip.addPara("A portion of the resources produced by the colony will be made available here. "
                        + "These resources can be extracted from the colony's economy for a cost equal to %s of their base value. "
                        + "This cost will be deducted at the end of the month.", opad,
                Misc.getHighlightColor(), "" + (int) Math.round(
                        LocalResourcesSubmarketPlugin.STOCKPILE_COST_MULT * 100f) + "%");

        tooltip.addPara("These resources can also be used to counter temporary shortages, for a "
                        + "cost equal to %s of their base value. If additional resources are placed here, they "
                        + "will be used as well, at no cost.", opad,
                Misc.getHighlightColor(), "" + (int) Math.round(
                        LocalResourcesSubmarketPlugin.STOCKPILE_SHORTAGE_COST_MULT * 100f) + "%");

        tooltip.addSectionHeading("Stockpiled per month",
                plugin.getMarket().getFaction().getBaseUIColor(),
                plugin.getMarket().getFaction().getDarkUIColor(),
                Alignment.MID, opad);
        if (rowIndex > 0) {
            tooltip.addGrid(opad);
            tooltip.addPara("Stockpiles are limited to %s the monthly rate.", opad,
                    Misc.getHighlightColor(), ""
                            + (int) LocalResourcesSubmarketPlugin.STOCKPILE_MAX_MONTHS
                            + Strings.X);
        } else {
            tooltip.addPara("No stockpiling.", opad);
        }
    }

    /**
     * Returns a read-only AoTD limit from the last published supply/demand data,
     * or null when the caller must use its existing correctness fallback once.
     */
    public static Integer peekAoTDStockpileLimit(
            AoTDCommodityOnMarket commodity,
            Map<String, MutableStat> stockpilingBonus) {
        AoTDSupplyDemandData data = commodity.peekSupplyDemandData();
        if (data == null) return null;

        int limit = data.getTotalRawUnitsFromDemand();
        String commodityId = commodity.getId();
        if (stockpilingBonus.containsKey(commodityId)) {
            limit += AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(
                    (int) stockpilingBonus.get(commodityId).getModifiedValue(),
                    true, commodityId);
        }
        limit *= LocalResourcesSubmarketPlugin.STOCKPILE_MAX_MONTHS;

        int deficitCountered = 0;
        for (Map.Entry<String, MutableStat.StatMod> entry
                : commodity.getExcDefData().deficit.getFlatMods().entrySet()) {
            if (entry.getKey().contains("aotd_shortage_counter")) {
                deficitCountered += (int) Math.abs(entry.getValue().value);
            }
        }
        if (commodity.getExcDefData().getDeficit() - deficitCountered > 0) return 0;
        return Math.max(0, limit);
    }
}
