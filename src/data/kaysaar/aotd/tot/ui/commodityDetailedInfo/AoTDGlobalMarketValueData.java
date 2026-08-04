package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.ScavengerGuildUtils;
import java.awt.*;

public class AoTDGlobalMarketValueData implements TooltipMakerAPI.TooltipCreator {
    FactionAPI faction;
    String commodityId;

    public AoTDGlobalMarketValueData(String commodityId, FactionAPI faction) {
        this.faction = faction;
        this.commodityId = commodityId;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 500;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.addTitle("Global Market Value");

        tooltip.addPara(
                "Represents the total value of sector-wide demand, assuming all commodities are traded externally.",
                8f);

        int gProd =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId)
                        + ScavengerGuildUtils.getCoveredAmountFromSector(commodityId);
        int gDem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);

        int price =
                AoTDSectorProductionDemandDataUtils.getPriceAmountTotalAroundSectorForFaction(
                        commodityId, gDem, gProd, faction.getId());

        tooltip.addPara(
                "For the %s, the maximum potential trade value under current conditions is %s.",
                5f,
                Color.ORANGE,
                AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()),
                Misc.getDGSCredits(price));

        tooltip.addPara(
                "This value can be increased by making other factions more reliant on your exports. Disrupting or raiding competing industries will introduce void in supply that \"someone\" must fill in.",
                5f);
    }
}
