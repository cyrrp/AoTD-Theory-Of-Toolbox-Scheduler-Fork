package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import java.util.HashSet;
import kaysaar.bmo.listeners.BuildingMenuListener;

public class BMOWonderBlockerListener implements BuildingMenuListener {
    @Override
    public HashSet<String> addBuildingsToBeHidden(MarketAPI marketAPI) {
        HashSet<String> all = new HashSet<>();
        for (Industry industry : marketAPI.getIndustries()) {
            if (industry instanceof GrandWonderAPI wonderAPI) {
                if (wonderAPI.getIndustriesToPreventFromAppearingInMenu(marketAPI) != null) {
                    all.addAll(wonderAPI.getIndustriesToPreventFromAppearingInMenu(marketAPI));
                }
            }
            if (industry instanceof AoTDConstructionSite site && industry.isUpgrading()) {
                if (site.getWonderAPI().getIndustriesToPreventFromAppearingInMenu(marketAPI)
                        != null) {
                    all.addAll(
                            site.getWonderAPI()
                                    .getIndustriesToPreventFromAppearingInMenu(marketAPI));
                }
            }
        }
        GrandWonderTypeManager.getIndSpecsOfWonders().forEach(x -> all.add(x.getId()));
        return all;
    }
}
