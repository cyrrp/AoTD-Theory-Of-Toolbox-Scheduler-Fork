package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.listeners.CommodityTooltipModifier;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class AoTDCommodityTooltipInjector implements CommodityTooltipModifier {
    @Override
    public void addSectionAfterPrice(
            TooltipMakerAPI info, float width, boolean expanded, CargoStackAPI stack) {
        if (expanded && stack.isCommodityStack()) {
            info.addCustom(
                    new AoTDPriceTableRemoval(info, stack.getCommodityId()).getMainPanel(), 0f);
        }
    }
}
