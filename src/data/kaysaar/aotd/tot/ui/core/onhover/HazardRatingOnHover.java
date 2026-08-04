package data.kaysaar.aotd.tot.ui.core.onhover;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;

public class HazardRatingOnHover implements TooltipMakerAPI.TooltipCreator {

    private final MarketAPI market;

    public HazardRatingOnHover(MarketAPI market) {
        this.market = market;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 450;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {

        float paddingSmall = 3.0F;
        float paddingMedium = 5.0F;
        float paddingLarge = 10.0F;

        Color highlightColor = Misc.getHighlightColor();
        Color negativeColor = Misc.getNegativeHighlightColor();

        FactionAPI faction = market.getFaction();
        Color baseColor = faction.getBaseUIColor();
        Color darkColor = faction.getDarkUIColor();
        Color gridColor = faction.getGridUIColor();
        Color brightColor = faction.getBrightUIColor();

        float hazardValue = market.getHazard().getModifiedValue();
        String hazardPercent = Math.round(hazardValue * 100.0F) + "%";

        tooltip.addTitle("Hazard Rating", baseColor);

        tooltip.addPara(
                "The hazard rating at "
                        + market.getName()
                        + " is %s. The following contributing factors were identified:",
                paddingLarge,
                highlightColor,
                new String[] {hazardPercent});

        tooltip.addStatModGrid(
                getTooltipWidth(tooltipParam),
                50.0F,
                paddingLarge,
                paddingMedium,
                market.getHazard(),
                new TooltipMakerAPI.StatModValueGetter() {

                    @Override
                    public String getPercentValue(MutableStat.StatMod mod) {
                        String sign = mod.getValue() > 0.0F ? "+" : "";
                        return sign + (int) mod.getValue() + "%";
                    }

                    @Override
                    public String getMultValue(MutableStat.StatMod mod) {
                        return "×" + Misc.getRoundedValue(mod.getValue());
                    }

                    @Override
                    public String getFlatValue(MutableStat.StatMod mod) {
                        String sign = mod.getValue() > 0.0F ? "+" : "";
                        return sign + (int) (mod.getValue() * 100.0F) + "%";
                    }

                    @Override
                    public Color getModColor(MutableStat.StatMod mod) {
                        return null;
                    }
                });

        float upkeepMultiplier =
                PopulationAndInfrastructure.getUpkeepHazardMult(market.getHazardValue());
        float immigrationPerHazard = Global.getSettings().getFloat("immigrationPerHazard");

        String upkeepPercent = Math.round(upkeepMultiplier * 100.0F) + "%";
        String upkeepMultString = "×" + Misc.getRoundedValueFloat(upkeepMultiplier);

        float immigrationPenalty = CoreImmigrationPluginImpl.getImmigrationHazardPenalty(market);
        float immigrationPenaltySizeMult = 0.0F;

        if (immigrationPenalty != 0.0F) {
            immigrationPenaltySizeMult =
                    CoreImmigrationPluginImpl.getImmigrationHazardPenaltySizeMult(market);
        }

        float baseHazard = market.getHazardValue();
        int penaltyMagnitude = Math.round(Math.abs(immigrationPenalty));

        String effectVerb = "reduces";
        String unit = "points";

        if (baseHazard < 1.0F) {
            effectVerb = "increases";
        }

        if (penaltyMagnitude == 1) {
            unit = "point";
        }

        if (penaltyMagnitude == 0) {
            tooltip.addPara(
                    "The hazard rating results in a %s upkeep multiplier.",
                    paddingLarge, highlightColor, new String[] {upkeepPercent});
        } else {
            tooltip.addPara(
                    "The hazard rating results in a %s upkeep multiplier, and "
                            + effectVerb
                            + " the population growth rate by %s "
                            + unit
                            + ". The growth penalty increases with colony size.",
                    paddingLarge,
                    highlightColor,
                    new String[] {upkeepMultString, String.valueOf(penaltyMagnitude)});
        }
    }
}
