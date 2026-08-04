// file: data/kaysaar/aotd/tot/scripts/trade/ScavengerGuildUtils.java
package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.Global;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.strings.AoTDTradeTags;
import java.util.HashMap;
import java.util.Map;

/**
 * Scavenger Guild rule: If totalDemand > totalProduction * (1 + threshold), scavengers cover enough
 * to bring effective demand down to production*(1+threshold).
 *
 * <p>Covered amount = max(0, totalDemand - totalProduction*(1+threshold)).
 *
 * <p>Threshold default: 0.10 (10%). Override per commodity via setThreshold().
 */
public final class ScavengerGuildUtils {

    private static final Map<String, Float> THRESHOLD_BY_COMMODITY = new HashMap<>();
    private static float DEFAULT_THRESHOLD = 0.10f;

    /** Optional global default, if you want to tune the baseline. */
    public static void setDefaultThreshold(float threshold) {
        DEFAULT_THRESHOLD = threshold;
    }

    /** Per-commodity override (e.g. food=0.05, heavy_machinery=0.15). */
    public static void setThreshold(String commodityId, float threshold) {
        THRESHOLD_BY_COMMODITY.put(commodityId, threshold);
    }

    public static float getThreshold(String commodityId) {
        return THRESHOLD_BY_COMMODITY.getOrDefault(commodityId, DEFAULT_THRESHOLD);
    }

    // ----------------------------
    // Core coverage logic
    // ----------------------------

    /** True if scavengers should cover some amount, given totals. */
    public static boolean doesCoverCommodity(
            String commodityId, int totalDemand, int totalProduction) {
        return getCoveredAmount(commodityId, totalDemand, totalProduction) > 0;
    }

    /** Covered amount using already-known totals. */
    public static int getCoveredAmount(String commodityId, int totalDemand, int totalProduction) {
        if (Global.getSettings()
                .getCommoditySpec(commodityId)
                .hasTag(AoTDTradeTags.IGNORE_SCAVENGERS)) return 0;
        if (totalDemand <= 0 || totalProduction <= 0) return 0;

        float threshold = getThreshold(commodityId);
        double allowedDemand = totalProduction * (1.0 + threshold);

        if (totalDemand <= allowedDemand) return 0;

        return (int) Math.ceil(totalDemand - allowedDemand);
    }

    /** Covered amount using GLOBAL sector totals (auto fetch). */
    public static int getCoveredAmountFromSector(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getCoveredAmount(commodityId, dem, prod);
    }

    /** True if scavengers should cover some amount using GLOBAL sector totals. */
    public static boolean doesCoverCommodityFromSector(String commodityId) {
        return getCoveredAmountFromSector(commodityId) > 0;
    }

    // ----------------------------
    // Percentage / ratio helpers
    // ----------------------------

    /** Returns demand/production ratio (e.g. 1.10 = demand is 10% higher than production). */
    public static float getDemandToProductionRatio(int totalDemand, int totalProduction) {
        if (totalProduction <= 0) return 0f;
        return (float) totalDemand / (float) totalProduction;
    }

    /** Returns percent demand exceeds production: e.g. 0.12 => 12% over. */
    public static float getOverageRatio(int totalDemand, int totalProduction) {
        if (totalProduction <= 0) return 0f;
        return ((float) totalDemand / (float) totalProduction) - 1f;
    }

    /** Returns percent demand exceeds production, as 0..100 (e.g. 12.0f). */
    public static float getOveragePercent(int totalDemand, int totalProduction) {
        return getOverageRatio(totalDemand, totalProduction) * 100f;
    }

    /** Convenience: sector demand/production ratio for a commodity. */
    public static float getSectorDemandToProductionRatio(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getDemandToProductionRatio(dem, prod);
    }

    /** Convenience: sector overage percent for a commodity. */
    public static float getSectorOveragePercent(String commodityId) {
        int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        return getOveragePercent(dem, prod);
    }
}
