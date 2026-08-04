package data.kaysaar.aotd.tot.grandwonders;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public interface GrandWonderAPI extends Industry {
    public LinkedHashMap<String, Integer> getDemandCostForRestoration();

    public void finishedConstruction(MarketAPI market);

    public String getWonderTypeId();

    public void addToCustomSectionInTooltip(TooltipMakerAPI tooltip);

    public LinkedHashMap<String, String> getRequirementsToBuildWonder();

    public boolean hasReqBeenMetOnMarket(String id);

    public LinkedHashSet<String> getIndustriesToPreventFromAppearingInMenu(MarketAPI market);

    public boolean shouldShowInListOfWonders(MarketAPI market);
}
