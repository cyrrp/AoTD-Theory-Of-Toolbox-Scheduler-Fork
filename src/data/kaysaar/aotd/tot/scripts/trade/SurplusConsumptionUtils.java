package data.kaysaar.aotd.tot.scripts.trade;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SurplusConsumptionUtils {

    /** Which commodities are covered by the surplus-cap system. */
    private static final Set<String> COVERED = new HashSet<>();

    /** cap percent per commodity (default 10%). */
    private static final Map<String, Float> CAP_BY_COMMODITY = new HashMap<>();

    static {
        // Example defaults (edit to your needs)
        // COVERED.add("food");
        // COVERED.add("supplies");
        // COVERED.add("fuel");

        // Optional per commodity overrides
        // CAP_BY_COMMODITY.put("food", 0.15f);
    }

    public static boolean doesCoverCommodity(String commodityId) {
        return commodityId != null && COVERED.contains(commodityId);
    }

    public static void addCoveredCommodity(String commodityId) {
        if (commodityId != null) COVERED.add(commodityId);
    }

    public static void removeCoveredCommodity(String commodityId) {
        if (commodityId != null) COVERED.remove(commodityId);
    }

    public static float getCapPercent(String commodityId) {
        // default 10%
        return CAP_BY_COMMODITY.getOrDefault(commodityId, 0.10f);
    }

    public static void setCapPercent(String commodityId, float cap) {
        if (commodityId == null) return;
        CAP_BY_COMMODITY.put(commodityId, cap);
    }
}
