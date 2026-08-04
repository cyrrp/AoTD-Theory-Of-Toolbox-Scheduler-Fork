package data.kaysaar.aotd.tot.ui.grandwonders;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.ImagePanel;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeSpecAPI;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kaysaar.bmo.buildingmenu.BuildingMenuMisc;

public class CurrentGrantWonderView implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    GrandWonderAPI wonderAPI;

    public CurrentGrantWonderView(GrandWonderAPI wonderAPI, float width, float height) {
        this.wonderAPI = wonderAPI;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI subTooltip =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 130, false);
        float heightRest = 70;
        TooltipMakerAPI rest =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(), heightRest, true);
        TooltipMakerAPI tlContent =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight() - 140 - heightRest,
                        true);
        subTooltip.setTitleOrbitronLarge();
        LabelAPI label = subTooltip.addTitle(wonderAPI.getCurrentName());
        label.getPosition()
                .inTL(
                        (mainPanel.getPosition().getWidth() / 2)
                                - (label.computeTextWidth(label.getText()) / 2),
                        0);
        UILinesRenderer renderer = new UILinesRenderer(0f);
        ImagePanel panel = new ImagePanel();
        CustomPanelAPI panelHolder =
                Global.getSettings().createCustom(mainPanel.getPosition().getWidth(), 95, renderer);
        CustomPanelAPI panelImage = panelHolder.createCustomPanel(190, 95, panel);
        renderer.setPanel(panelImage);
        panel.init(panelImage, Global.getSettings().getSprite(wonderAPI.getCurrentImage()));
        panelHolder.addComponent(panelImage).inTL(mainPanel.getPosition().getWidth() / 2 - 95, 0);
        GrandWonderTypeSpecAPI specAPI =
                GrandWonderTypeManager.getSpec(wonderAPI.getWonderTypeId());
        subTooltip
                .addCustom(panelHolder, 0f)
                .getPosition()
                .inTL(0, -label.getPosition().getY() + 7);

        BuildingMenuMisc.createTooltipForIndustry(
                (BaseIndustry) wonderAPI,
                Industry.IndustryTooltipMode.ADD_INDUSTRY,
                tlContent,
                false,
                false,
                contentPanel.getPosition().getWidth() - 15,
                true,
                false,
                false);
        tlContent.addSectionHeading("Grand Wonder Requirements", Alignment.MID, 0f);
        tlContent.setBulletedListMode(BaseIntelPlugin.BULLET);
        for (Map.Entry<String, String> entry :
                wonderAPI.getRequirementsToBuildWonder().entrySet()) {
            if (wonderAPI.hasReqBeenMetOnMarket(entry.getKey())) {
                tlContent.addPara(entry.getValue(), Misc.getPositiveHighlightColor(), 3f);
            } else {
                tlContent.addPara(entry.getValue(), Misc.getNegativeHighlightColor(), 3f);
            }
        }
        if (specAPI.isUniqueViaCategory()) {
            tlContent.addPara(
                    "In total our faction can built up to %s of wonders of type %s",
                    3f,
                    specAPI.getColor(),
                    specAPI.getMaxAmountOfWonderOfType(wonderAPI.getId(), wonderAPI.getMarket())
                            + "",
                    specAPI.getName());

        } else {
            tlContent.addPara(
                    "In total our faction can built up to %s of %s",
                    3f,
                    Color.ORANGE,
                    specAPI.getMaxAmountOfWonderOfType(wonderAPI.getId(), wonderAPI.getMarket())
                            + "",
                    wonderAPI.getCurrentName());
        }

        rest.addSectionHeading(
                "Monthly resource cost required for construction", Alignment.MID, 0f);
        LinkedHashMap<String, Integer> am = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry :
                wonderAPI.getDemandCostForRestoration().entrySet()) {
            am.put(
                    entry.getKey(),
                    AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(
                            entry.getValue(), true, entry.getKey()));
        }

        AoTDCommodityShortPanelCombined combined =
                new AoTDCommodityShortPanelCombined(
                        contentPanel.getPosition().getWidth() - 15, 5, am);
        rest.addCustom(combined.getMainPanel(), 5f);
        rest.setBulletedListMode(null);

        contentPanel.addUIElement(subTooltip).inTL(0, 0);
        contentPanel.addUIElement(tlContent).inTL(0, 130);
        contentPanel
                .addUIElement(rest)
                .inTL(0, contentPanel.getPosition().getHeight() - heightRest);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public void setContentPanel(CustomPanelAPI contentPanel) {
        this.contentPanel = contentPanel;
        createUI();
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
