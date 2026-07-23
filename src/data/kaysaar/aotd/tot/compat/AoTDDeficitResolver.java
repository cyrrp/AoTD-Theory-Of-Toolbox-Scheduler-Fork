package data.kaysaar.aotd.tot.compat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;

/**
 * Source-level AoTD priority-deficit semantics used by the clean BaseIndustry
 * wrapper installed by StarsectorPrepatcher.
 */
public final class AoTDDeficitResolver {
    private AoTDDeficitResolver() {}

    public static Object resolve(Object rawIndustry, Object rawCommodityIds) {
        if (!(rawIndustry instanceof Industry target)
                || !(rawCommodityIds instanceof String[] commodityIds)) {
            throw new IllegalArgumentException("Invalid AoTD deficit resolver arguments");
        }
        Pair<String, Integer> result = new Pair<>();
        result.two = 0;
        if (Global.CODEX_TOOLTIP_MODE) return result;

        MarketAPI market = target.getMarket();
        AoTDIndustryData industryData = AoTDIndustryData.getInstance(market);
        String targetId = target.getId();
        for (String commodityId : commodityIds) {
            int demand = (int) target.getDemand(commodityId)
                    .getQuantity().getModifiedValue();
            CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
            int available = commodity.getAvailable();

            for (Industry industry : industryData.getStableIndustryOrder(market)) {
                if (industry.getId().equals(targetId)) break;
                int priorDemand = (int) industry.getDemand(commodityId)
                        .getQuantity().getModifiedValue();
                available -= Math.max(0, priorDemand);
            }

            int deficit = Math.max(demand - available, 0);
            if (deficit > demand) deficit = demand;
            if (deficit > result.two) {
                result.one = commodityId;
                result.two = deficit;
            }
        }
        return result;
    }
}
