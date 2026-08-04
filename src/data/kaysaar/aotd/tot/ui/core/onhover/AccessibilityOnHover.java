package data.kaysaar.aotd.tot.ui.core.onhover;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import java.awt.*;

public class AccessibilityOnHover implements TooltipMakerAPI.TooltipCreator {

    private final MarketAPI market;

    public AccessibilityOnHover(MarketAPI market) {
        this.market = market;
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

        tooltip.addTitle("Accessibility", baseColor);

        int accessibilityPercent =
                Math.round(market.getAccessibilityMod().computeEffective(0.0F) * 100.0F);

        Color accessibilityColor = highlightColor;
        if (accessibilityPercent <= 0) {
            accessibilityColor = negativeColor;
        }

        tooltip.addPara(
                "Accessibility reflects how attractive this colony is to %s, both domestic and foreign, and determines its priority in %s.",
                paddingMedium, highlightColor, "traders", "trade");

        tooltip.addPara(
                "Accessibility: %s",
                paddingLarge, accessibilityColor, new String[] {accessibilityPercent + "%"});

        tooltip.addStatModGrid(
                getTooltipWidth(tooltipParam),
                50.0F,
                paddingLarge,
                paddingSmall,
                market.getAccessibilityMod(),
                new TooltipMakerAPI.StatModValueGetter() {

                    @Override
                    public String getPercentValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    @Override
                    public String getMultValue(MutableStat.StatMod mod) {
                        return null;
                    }

                    @Override
                    public Color getModColor(MutableStat.StatMod mod) {
                        return mod.value < 0.0F ? Misc.getNegativeHighlightColor() : null;
                    }

                    @Override
                    public String getFlatValue(MutableStat.StatMod mod) {
                        int percent = Math.round(mod.value * 100.0F);
                        return mod.value >= 0.0F ? "+" + percent + "%" : percent + "%";
                    }
                });

        tooltip.addSectionHeading(
                "Trade Priority", baseColor, darkColor, Alignment.MID, paddingLarge);

        tooltip.addPara(
                "Colonies with higher accessibility are more likely to participate in %s, allowing them to more reliably %s and %s.",
                paddingMedium,
                highlightColor,
                "external trade",
                "import goods",
                "export their products");

        tooltip.addPara(
                "During %s, low-accessibility colonies may be unable to %s. During %s, they may fail to %s, resulting in %s.",
                paddingMedium,
                highlightColor,
                "global surpluses",
                "export their goods",
                "global shortages",
                "import enough goods",
                "shortages");
        tooltip.addPara(
                "If a colony's accessibility falls to %s or below, it will be unable to participate in external trade entirely, preventing it from both importing and exporting goods.",
                paddingMedium, highlightColor, "0");
        if (market.getAccessibilityMod().computeEffective(0f) <= 0 || !market.hasSpaceport()) {
            tooltip.addPara(
                    "Warning, this market is unable to participate in trade, can't export, nor import goods!",
                    Misc.getNegativeHighlightColor(),
                    3f);
        }
        int totalMarkets = Global.getSector().getEconomy().getNumMarkets() - 1;
        int marketsWithHigherAccessibility =
                AoTDToolboxMisc.getAmountOfMarketsGreaterAccThanTargetedMarket(
                        Global.getSector().getEconomy().getMarketsCopy(), market);

        if (marketsWithHigherAccessibility <= 0) {
            tooltip.addPara(
                    "No other markets have higher accessibility than %s.",
                    paddingLarge, Color.ORANGE, market.getName());
        } else {
            tooltip.addPara(
                    "%s out of %s markets have higher accessibility than %s.",
                    paddingLarge,
                    Color.ORANGE,
                    String.valueOf(marketsWithHigherAccessibility),
                    String.valueOf(totalMarkets),
                    market.getName());
        }
    }
}
