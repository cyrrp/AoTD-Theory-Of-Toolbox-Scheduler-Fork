package data.kaysaar.aotd.tot.scripts.trade.history;

import java.util.LinkedHashMap;

public class FactionProductionData {
    int month;
    LinkedHashMap<String, Integer> production = new LinkedHashMap<>();
    LinkedHashMap<String, Integer> demand = new LinkedHashMap<>();

    public FactionProductionData(
            int month,
            LinkedHashMap<String, Integer> production,
            LinkedHashMap<String, Integer> demand) {
        this.month = month;
        this.production = production;
        this.demand = demand;
    }

    public int getMonth() {
        return month;
    }

    public LinkedHashMap<String, Integer> getProduction() {
        return production;
    }

    public LinkedHashMap<String, Integer> getDemand() {
        return demand;
    }

    public int getProductionValueFromMonth(String commodityId) {
        return getProduction().getOrDefault(commodityId, 0);
    }

    public int getDemandValueFromMonth(String commodityId) {
        return getDemand().getOrDefault(commodityId, 0);
    }
}
