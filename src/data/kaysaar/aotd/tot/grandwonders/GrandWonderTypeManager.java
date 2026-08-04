package data.kaysaar.aotd.tot.grandwonders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class GrandWonderTypeManager {
    public static LinkedHashMap<String, GrandWonderTypeSpecAPI> specs = new LinkedHashMap<>();

    public static void addNewSpec(GrandWonderTypeSpecAPI specAPI) {
        specs.put(specAPI.getId(), specAPI);
    }

    public static GrandWonderTypeSpecAPI getSpec(String id) {
        return specs.get(id);
    }

    public static ArrayList<IndustrySpecAPI> getIndSpecsOfWonders() {
        ArrayList<IndustrySpecAPI> indSpecs = new ArrayList<>();
        for (IndustrySpecAPI industrySpecAPI : Global.getSettings().getAllIndustrySpecs()) {
            try {
                if (industrySpecAPI.getNewPluginInstance(null) instanceof GrandWonderAPI) {
                    indSpecs.add(industrySpecAPI);
                }
            } catch (Exception ignored) {
            }
        }
        return indSpecs;
    }

    public static ArrayList<GrandWonderAPI> getWondersVisibleForMarket(MarketAPI market) {
        ArrayList<GrandWonderAPI> indSpecs = new ArrayList<>();
        for (IndustrySpecAPI industrySpecAPI : Global.getSettings().getAllIndustrySpecs()) {
            if (industrySpecAPI.getNewPluginInstance(market) instanceof GrandWonderAPI wonderAPI) {
                if (wonderAPI.shouldShowInListOfWonders(market)) {
                    indSpecs.add(wonderAPI);
                }
            }
        }
        if (market != null) {
            for (Industry industry : market.getIndustries()) {
                GrandWonderAPI wonderAPI = null;
                if (industry instanceof AoTDConstructionSite site && site.isUpgrading()) {
                    wonderAPI = site.getWonderAPI();
                }
                if (industry instanceof GrandWonderAPI) {
                    wonderAPI = (GrandWonderAPI) industry;
                }
                if (wonderAPI != null) {
                    GrandWonderTypeSpecAPI specAPI = specs.get(wonderAPI.getWonderTypeId());
                    if (specAPI.isUniqueViaCategory()) {
                        GrandWonderAPI finalWonderAPI = wonderAPI;
                        indSpecs.removeIf(
                                x -> x.getWonderTypeId().equals(finalWonderAPI.getWonderTypeId()));
                    } else {
                        GrandWonderAPI finalWonderAPI = wonderAPI;
                        indSpecs.removeIf(x -> x.getId().equals(finalWonderAPI.getId()));
                    }
                }
            }
        }
        return indSpecs;
    }

    public static ArrayList<GrandWonderAPI> getWondersInstances(MarketAPI market) {
        ArrayList<GrandWonderAPI> indSpecs = new ArrayList<>();
        for (IndustrySpecAPI industrySpecAPI : Global.getSettings().getAllIndustrySpecs()) {
            if (industrySpecAPI.getNewPluginInstance(market) instanceof GrandWonderAPI wonderAPI) {

                indSpecs.add(wonderAPI);
            }
        }
        return indSpecs;
    }
}
