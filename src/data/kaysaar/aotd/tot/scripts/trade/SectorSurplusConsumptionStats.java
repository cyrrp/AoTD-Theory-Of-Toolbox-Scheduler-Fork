// file: data/kaysaar/aotd/tot/scripts/trade/SectorSurplusConsumptionStats.java
package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.Global;
import java.util.HashMap;
import java.util.Map;

public class SectorSurplusConsumptionStats {
    private static final String memKey = "$aotd_sector_surplus_consumption_solver";

    public static SectorSurplusConsumptionStats getInstance() {
        if (!Global.getSector().getPersistentData().containsKey(memKey)) {
            Global.getSector().getPersistentData().put(memKey, new SectorSurplusConsumptionStats());
        }
        return (SectorSurplusConsumptionStats) Global.getSector().getPersistentData().get(memKey);
    }

    /** commodityId -> factionId -> amountConsumed */
    private final Map<String, Map<String, Integer>> consumedByCommodityByFaction = new HashMap<>();

    /** commodityId -> totalConsumed */
    private final Map<String, Integer> totalConsumedByCommodity = new HashMap<>();

    public void clear() {
        consumedByCommodityByFaction.clear();
        totalConsumedByCommodity.clear();
    }

    public void record(String commodityId, String factionId, int amount) {
        if (amount <= 0) return;
        if (commodityId == null || factionId == null) return;

        consumedByCommodityByFaction
                .computeIfAbsent(commodityId, k -> new HashMap<>())
                .merge(factionId, amount, Integer::sum);

        totalConsumedByCommodity.merge(commodityId, amount, Integer::sum);
    }

    public Map<String, Map<String, Integer>> getConsumedByCommodityByFaction() {
        return consumedByCommodityByFaction;
    }

    public Map<String, Integer> getTotalConsumedByCommodity() {
        return totalConsumedByCommodity;
    }

    public int getConsumed(String commodityId, String factionId) {
        Map<String, Integer> byFaction = consumedByCommodityByFaction.get(commodityId);
        if (byFaction == null) return 0;
        return byFaction.getOrDefault(factionId, 0);
    }

    public int getTotalConsumed(String commodityId) {
        return totalConsumedByCommodity.getOrDefault(commodityId, 0);
    }
}
