package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.ScavengerGuildUtils;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class AoTDDetailedComPanelOnHoverImpExp implements TooltipMakerAPI.TooltipCreator {
    boolean isProduction = false;
    String commodityId;
    FactionAPI faction;

    public AoTDDetailedComPanelOnHoverImpExp(
            boolean isProduction, String commodityId, FactionAPI factionCol) {
        this.isProduction = isProduction;
        this.commodityId = commodityId;
        this.faction = factionCol;
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
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
        if (isProduction) {
            tooltip.addTitle("Global Production: " + spec.getName());
            int amount = ScavengerGuildUtils.getCoveredAmountFromSector(commodityId);
            int supply =
                    AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
            tooltip.addPara(
                    "Currently sector is able to produce in total %s units of %s",
                    3f, Color.ORANGE, Misc.getWithDGS(supply + amount), spec.getName());
            tooltip.addSectionHeading(
                    "Producers",
                    faction.getBaseUIColor(),
                    faction.getDarkUIColor(),
                    Alignment.MID,
                    5f);
            List<FactionAPI> economyFactions =
                    AoTDToolboxMisc.getFactionsInEconomy().stream()
                            .filter(
                                    x ->
                                            AoTDSectorProductionDemandDataUtils
                                                            .getTotalProductionFromFaction(
                                                                    commodityId, x.getId())
                                                    > 0)
                            .sorted(
                                    new Comparator<FactionAPI>() {
                                        @Override
                                        public int compare(FactionAPI o1, FactionAPI o2) {
                                            return Integer.compare(
                                                    AoTDSectorProductionDemandDataUtils
                                                            .getTotalProductionFromFaction(
                                                                    commodityId, o2.getId()),
                                                    AoTDSectorProductionDemandDataUtils
                                                            .getTotalProductionFromFaction(
                                                                    commodityId, o1.getId()));
                                        }
                                    })
                            .toList();
            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
            int currAm = supply;
            int i = 0;
            for (FactionAPI faction : economyFactions) {
                if (i >= 10) {
                    tooltip.addPara(
                            "Rest of factions: %s", 10f, Color.ORANGE, Misc.getWithDGS(currAm));
                    break;
                }
                int am =
                        AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                                commodityId, faction.getId());
                tooltip.addPara(
                        AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()) + ": %s",
                        3f,
                        faction.getBaseUIColor(),
                        Color.ORANGE,
                        Misc.getWithDGS(am));
                currAm -= am;
                i++;
            }
            tooltip.setBulletedListMode(null);
            if (amount > 0) {
                tooltip.addPara(
                        "Due to demand exceeding global production capacity, the %s have expanded their operations and are supplying an additional %s units of %s to the global market.",
                        10f,
                        new Color[] {Misc.getGrayColor(), Color.ORANGE, Color.ORANGE},
                        "Scavenger Guild",
                        Misc.getWithDGS(amount),
                        spec.getName());
            }

        } else {
            tooltip.addTitle("Global Demand: " + spec.getName());
            int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
            int supply =
                    AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
            int effectiveDemand =
                    AoTDSectorProductionDemandDataUtils
                            .getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts(
                                    commodityId, Factions.PLAYER);
            tooltip.addPara(
                    "Currently sector is consuming in total around %s units of %s",
                    3f, Color.ORANGE, Misc.getWithDGS(dem), spec.getName());
            tooltip.addSectionHeading(
                    "Consumers",
                    faction.getBaseUIColor(),
                    faction.getDarkUIColor(),
                    Alignment.MID,
                    5f);
            List<FactionAPI> economyFactions =
                    AoTDToolboxMisc.getFactionsInEconomy().stream()
                            .filter(
                                    x ->
                                            AoTDSectorProductionDemandDataUtils
                                                            .getTotalProductionFromFaction(
                                                                    commodityId, x.getId())
                                                    > 0)
                            .sorted(
                                    new Comparator<FactionAPI>() {
                                        @Override
                                        public int compare(FactionAPI o1, FactionAPI o2) {
                                            return Integer.compare(
                                                    AoTDSectorProductionDemandDataUtils
                                                            .getTotalDemandFromFactionExcludingContracts(
                                                                    commodityId, o2.getId()),
                                                    AoTDSectorProductionDemandDataUtils
                                                            .getTotalDemandFromFactionExcludingContracts(
                                                                    commodityId, o1.getId()));
                                        }
                                    })
                            .toList();
            int remDem = dem;
            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
            int i = 0;
            for (FactionAPI faction : economyFactions) {
                if (i >= 10) {
                    tooltip.addPara(
                            "Rest factions: %s", 10f, Color.ORANGE, Misc.getWithDGS(remDem));
                    break;
                }
                int curr =
                        AoTDSectorProductionDemandDataUtils
                                .getTotalDemandFromFactionExcludingContracts(
                                        commodityId, faction.getId());
                tooltip.addPara(
                        AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()) + ": %s",
                        3f,
                        faction.getBaseUIColor(),
                        Color.ORANGE,
                        Misc.getWithDGS(curr));
                remDem -= curr;
                i++;
            }
            tooltip.setBulletedListMode(null);
            tooltip.addSectionHeading("Effective demand", Alignment.MID, 5f);
            tooltip.addPara(
                    "Approximately %s units of %s remain unmet by internal faction trade and must be fulfilled through external markets. Optimal sources are colonies with high %s and fully satisfied internal demand for this commodity.",
                    5f,
                    Color.ORANGE,
                    Misc.getWithDGS(effectiveDemand),
                    spec.getName(),
                    "accessibility");
            if (supply > dem) {
                tooltip.addPara(
                        "*Reported demand includes only officially recorded activity. Real consumption is likely far greater, as significant volumes of trade occur beyond the oversight of core world authorities.*",
                        Misc.getGrayColor(),
                        8f);
            }
        }
    }
}
