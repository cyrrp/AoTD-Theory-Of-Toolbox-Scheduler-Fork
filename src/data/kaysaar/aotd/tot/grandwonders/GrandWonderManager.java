package data.kaysaar.aotd.tot.grandwonders;

import ashlib.data.plugins.misc.AshMisc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import data.kaysaar.aotd.tot.strings.AoTDMarketStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class GrandWonderManager {
    public static String memKey = "$aotd_toolbox_grandwonders_manager";
    public LinkedHashMap<String, Integer> builtSoFar = new LinkedHashMap<>();

    public LinkedHashMap<String, Integer> getBuiltSoFar() {
        return builtSoFar;
    }

    public void addBuiltSoFar(String key, int value) {
        if (key == null) return;

        builtSoFar.merge(key, value, Integer::sum);
    }

    public static boolean canBuiltAdditionalWonderDueToSlots(MarketAPI market) {
        int am =
                (int)
                        market.getStats()
                                .getDynamic()
                                .getMod(AoTDMarketStats.AOTD_GRAND_WONDER_COUNT)
                                .computeEffective(0f);
        return getAmountOfWonders(market) < am;
    }

    public void removeBuiltSoFar(String key) {
        if (key == null) return;

        Integer current = builtSoFar.get(key);
        if (current == null) return;

        if (current <= 1) {
            builtSoFar.remove(key); // remove completely when 0 or below
        } else {
            builtSoFar.put(key, current - 1);
        }
    }

    public int getBuiltSoFar(String key) {
        int soFar = builtSoFar.getOrDefault(key, 0);
        for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
            for (Industry industry : marketAPI.getIndustries()) {
                if (industry instanceof AoTDConstructionSite site) {
                    if (site.getAssignedWonder() != null && site.getAssignedWonder().equals(key)) {
                        soFar++;
                    }
                }
            }
        }
        return soFar;
    }

    public boolean hadExceededLimitOfThisWonder(String id, String wonderType, MarketAPI marketAPI) {
        return getBuiltSoFar(id)
                < GrandWonderTypeManager.getSpec(wonderType)
                        .getMaxAmountOfWonderOfType(id, marketAPI);
    }

    public int getAmountOfWondersOfSameType(String wonderType) {
        int am = 0;
        for (MarketAPI marketAPI : AshMisc.getMarketsUnderPlayer()) {
            for (Industry industry : marketAPI.getIndustries()) {
                if (industry instanceof GrandWonderAPI wonderAPI) {
                    if (wonderAPI.getWonderTypeId().equals(wonderType)) {
                        am++;
                    }
                }
                if (industry instanceof AoTDConstructionSite site && industry.isUpgrading()) {
                    if (site.getAssignedWonder().equals(wonderType)) {
                        am++;
                    }
                }
            }
        }
        return am;
    }

    public static ArrayList<Industry> getIndustryWonders(MarketAPI market) {
        ArrayList<Industry> ind = new ArrayList<>();
        for (Industry industry : market.getIndustries()) {
            if (industry instanceof GrandWonderAPI || industry.getSpec().hasTag("grand_wonder")) {
                ind.add(industry);
            }
        }
        return ind;
    }

    public static int getAmountOfWonders(MarketAPI market) {
        int am = 0;
        for (Industry industry : market.getIndustries()) {
            if (industry instanceof GrandWonderAPI || industry.getSpec().hasTag("grand_wonder")) {
                am++;
            }
            if (industry instanceof AoTDConstructionSite site && industry.isUpgrading()) {
                am++;
            }
        }
        return am;
    }

    public static GrandWonderManager getInstance() {
        Global.getSector()
                .getPersistentData()
                .computeIfAbsent(memKey, k -> new GrandWonderManager());
        return (GrandWonderManager) Global.getSector().getPersistentData().get(memKey);
    }
}
