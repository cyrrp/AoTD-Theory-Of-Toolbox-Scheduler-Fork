package data.kaysaar.aotd.tot.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import java.io.IOException;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AoTDCommodityEconSpecManager {
    public static LinkedHashMap<String, AoTDCommodityEconSpec> specs = new LinkedHashMap<>();
    public static final String specsFilename = "data/campaign/aotd_econ_sheet.csv";
    public static LinkedHashSet<String> possibleCommoditiesProducedByIndustry =
            new LinkedHashSet<>();
    public static LinkedHashMap<String, AoTDSupDemListener> supDemListeners = new LinkedHashMap<>();

    public static void addListener(String id, AoTDSupDemListener listener) {
        supDemListeners.put(id, listener);
    }

    public static Collection<AoTDSupDemListener> getListeners() {
        return supDemListeners.values();
    }

    public static void removeListener(String id) {
        supDemListeners.remove(id);
    }

    public static void loadSpecs() {
        specs.clear();
        JSONArray array = null;
        try {
            array =
                    Global.getSettings()
                            .getMergedSpreadsheetDataForMod(
                                    "commodityId", specsFilename, "aotd_theory_of_toolbox");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                AoTDCommodityEconSpec spec = AoTDCommodityEconSpec.generateFromJson(obj);
                if (spec != null) {
                    specs.put(spec.getCommodityId(), spec);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public static int getCargoAmountFromSupplyOrDemand(
            int units, boolean isDemand, String commodityId) {
        MutableStat stat = new MutableStat(units);
        if (isDemand) {
            return getEconSpec(commodityId)
                    .getCalculationScript()
                    .getRawUnitsFromDemand(stat, null, commodityId, null);
        } else {
            return getEconSpec(commodityId)
                    .getCalculationScript()
                    .getRawUnitsFromSupply(stat, null, commodityId, null);
        }
    }

    public static float getCutForCommodity(String commodityId, boolean isInternal) {
        if (isInternal) {
            return getEconSpec(commodityId).getInternalCut();
        } else {
            return getEconSpec(commodityId).getExternalCut();
        }
    }

    public static AoTDCommodityEconSpec getEconSpec(String commodityId) {
        return specs.getOrDefault(
                commodityId,
                new AoTDCommodityEconSpec(
                        commodityId, 1, 1, AoTDBaseDemSupCalc.class.getName(), 0.05f, 0.15f));
    }
}
