package data.kaysaar.aotd.tot.ui.starsystems.onhover;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class MarketInfoOnHover implements TooltipMakerAPI.TooltipCreator {
    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 0;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {}
}
