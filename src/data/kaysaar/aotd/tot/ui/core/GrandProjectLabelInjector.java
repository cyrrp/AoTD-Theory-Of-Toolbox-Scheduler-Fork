package data.kaysaar.aotd.tot.ui.core;

import ashlib.data.plugins.ui.LabelWithHighlight;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderManager;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.CargoPanelContextUI;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.IndustryPanelContextUI;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.MarketUIListener;
import data.kaysaar.aotd.tot.scripts.coreui.listeners.SurveyPanelContextUI;
import data.kaysaar.aotd.tot.strings.AoTDMarketStats;
import java.awt.*;

public class GrandProjectLabelInjector implements MarketUIListener {
    @Override
    public void onMarketOverviewDiscovered(IndustryPanelContextUI ctx) {
        MarketAPI market = ctx.market;
        UIPanelAPI panelOfOtherInfo = ctx.panelOfOtherInfo;
        UIPanelAPI mainColonyPanel = ctx.mainColonyPanel;
        for (UIComponentAPI componentAPI : ReflectionUtilis.getChildrenCopy(mainColonyPanel)) {
            if (componentAPI instanceof CustomPanelAPI panel
                    && panel.getPlugin() instanceof LabelWithHighlight highlight) {
                if (highlight.getID().equals("label_grand_project")) return;
            }
        }
        LabelWithHighlight label = new LabelWithHighlight(300, 100, "label_grand_project");
        int number = GrandWonderManager.getAmountOfWonders(market);
        int available =
                (int)
                        market.getStats()
                                .getDynamic()
                                .getMod(AoTDMarketStats.AOTD_GRAND_WONDER_COUNT)
                                .computeEffective(0f);
        Color c = Color.ORANGE;
        if (available < number) {
            c = Misc.getNegativeHighlightColor();
        }

        label.addLabelHighlighted(
                "Grand Wonders:  %s",
                Fonts.INSIGNIA_LARGE,
                Misc.getGrayColor(),
                c,
                Alignment.TL,
                0f,
                number + " / " + available);
        label.setCreator(
                new TooltipMakerAPI.TooltipCreator() {
                    @Override
                    public boolean isTooltipExpandable(Object tooltipParam) {
                        return false;
                    }

                    @Override
                    public float getTooltipWidth(Object tooltipParam) {
                        return 500;
                    }

                    @Override
                    public void createTooltip(
                            TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                        tooltip.addPara(
                                "Grand wonders are unique structures, that are step above usual infrastructure or industry, providing unique bonuses or enormous production capabilities.",
                                3f);
                        tooltip.addPara(
                                "Given they are enormous undertaking, those structures will usually require enormous industrial capabilities!",
                                Misc.getTooltipTitleAndLightHighlightColor(),
                                5f);
                        tooltip.addPara(
                                "For every 2 Industry slots, you gain one additional Grand Wonder slot.",
                                Misc.getTooltipTitleAndLightHighlightColor(),
                                10f);
                        tooltip.addPara(
                                "Exceeding limit of Grand Wonder's will result in heavy income penalties!",
                                Misc.getNegativeHighlightColor(),
                                3f);
                        if (market.getFaction().isPlayerFaction()) {
                            tooltip.addSectionHeading(
                                    "Suitable Wonders for This Market", Alignment.MID, 5f);
                            if (GrandWonderTypeManager.getWondersVisibleForMarket(market)
                                    .isEmpty()) {
                                tooltip.addPara(
                                        "Currently no wonder, that we know of, can be built on this world.",
                                        Misc.getNegativeHighlightColor(),
                                        3f);
                            } else {
                                tooltip.addPara(
                                        "Those wonders can be built on this world. To start their construction , you need to construct %s !",
                                        3f, Color.ORANGE, "Build site");
                                tooltip.addSpacer(5f);
                                tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
                                for (GrandWonderAPI grandWonderAPI :
                                        GrandWonderTypeManager.getWondersVisibleForMarket(market)) {
                                    tooltip.addPara(grandWonderAPI.getCurrentName(), 3f);
                                }
                                tooltip.setBulletedListMode(null);
                            }
                        }
                        if (GrandWonderManager.getAmountOfWonders(market) > 0) {
                            tooltip.addSectionHeading(
                                    "Wonders present on this market", Alignment.MID, 10f);
                            tooltip.addSpacer(3f);
                            tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
                            for (Industry industryWonder :
                                    GrandWonderManager.getIndustryWonders(market)) {
                                tooltip.addPara(industryWonder.getCurrentName(), Color.ORANGE, 3f);
                            }
                            tooltip.setBulletedListMode(null);
                        }
                        tooltip.addSpacer(20f);
                        tooltip.addPara(
                                "Without stones, there is no building material, without building material, there is no palace—and without a palace, there is no palace.",
                                Misc.getGrayColor(),
                                3f);
                    }
                });
        label.createUI();
        mainColonyPanel.addComponent(label.getMainPanel()).inTL(500, 585);
    }

    @Override
    public void onSubmarketCargoCreated(CargoPanelContextUI ctx) {}

    @Override
    public void onSurveyPanelCreated(SurveyPanelContextUI ctx) {}
}
