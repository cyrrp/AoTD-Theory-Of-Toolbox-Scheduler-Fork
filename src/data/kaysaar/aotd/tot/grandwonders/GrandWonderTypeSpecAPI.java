package data.kaysaar.aotd.tot.grandwonders;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.awt.*;

public interface GrandWonderTypeSpecAPI {
    String getId();

    String getName();

    Color getColor();

    boolean isUniqueViaCategory();

    boolean showTypeSeparate();

    boolean canBuildAdditionalWonderOfType(String wonderId, MarketAPI market);

    public int getMaxAmountOfWonderOfType(String wonderId, MarketAPI market);

    public void createTooltipForTypeOfWonder(TooltipMakerAPI tooltipMakerAPI, MarketAPI market);
}
