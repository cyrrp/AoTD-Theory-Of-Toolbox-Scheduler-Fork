package data.kaysaar.aotd.tot.scripts.trade.history;

import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import java.util.LinkedHashMap;

public class FactionCycleProductionData {
    LinkedHashMap<Integer, FactionProductionData> cyclesOfData = new LinkedHashMap<>();
    String factionId;

    public FactionCycleProductionData(String factionId) {
        this.factionId = factionId;
    }

    public void doEndOfMonth(int month) {
        LinkedHashMap<String, Integer> productionData = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> demandData = new LinkedHashMap<>();
        for (String s : AoTDTradeManager.getInstance().getPossibleCommoditiesDemandedOrSupplied()) {
            int dem =
                    AoTDTradeManager.getInstance()
                            .getFactionTradeData(factionId)
                            .getFactionDemand(s);
            int sup =
                    AoTDTradeManager.getInstance()
                            .getFactionTradeData(factionId)
                            .getFactionSupply(s);
            productionData.put(s, sup);
            demandData.put(s, dem);
        }
        FactionProductionData data = new FactionProductionData(month, productionData, demandData);
        cyclesOfData.put(month, data);
    }

    public FactionProductionData getProductionFromMonth(int month) {
        if (cyclesOfData.containsKey(month)) {
            return cyclesOfData.get(month);
        }
        return null;
    }
}
