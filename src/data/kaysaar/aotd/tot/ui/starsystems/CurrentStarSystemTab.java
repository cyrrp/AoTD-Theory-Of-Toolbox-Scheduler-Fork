package data.kaysaar.aotd.tot.ui.starsystems;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.map.MapMainComponent;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.ui.starsystems.stuctures.StarSystemStructuresUI;
import java.util.List;

public class CurrentStarSystemTab implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    StarSystemAPI currStarSystem;
    MapMainComponent mapMainComponent;
    StarSystemStructuresUI struct;

    public CurrentStarSystemTab(float width, float height) {
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
    }

    public void setCurrStarSystem(StarSystemAPI currStarSystem) {
        this.currStarSystem = currStarSystem;
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
        if (mapMainComponent != null) {
            mapMainComponent.clearUI();
            mapMainComponent = null;
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        if (currStarSystem != null) {
            mapMainComponent =
                    new MapMainComponent(
                            contentPanel.getPosition().getWidth() - 5,
                            contentPanel.getPosition().getHeight() * 0.4f,
                            currStarSystem);
            TooltipMakerAPI tooltip =
                    contentPanel.createUIElement(
                            mapMainComponent.getMainPanel().getPosition().getWidth(),
                            mapMainComponent.getMainPanel().getPosition().getHeight() + 40,
                            false);
            tooltip.setParaFont(Fonts.ORBITRON_20AA);
            FactionAPI faction = AshMisc.getClaimingFaction(currStarSystem.getCenter());
            tooltip.addPara(currStarSystem.getName(), faction.getBaseUIColor(), 0f)
                    .setAlignment(Alignment.MID);
            tooltip.addCustom(mapMainComponent.getMainPanel(), 3f);
            contentPanel.addUIElement(tooltip).inTL(0, 0);
            createStarSystemInfoSection();
        }

        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public void createStarSystemInfoSection() {
        float heightLeft =
                contentPanel.getPosition().getHeight()
                        - mapMainComponent.getMainPanel().getPosition().getHeight()
                        - 30;
        float startingY = contentPanel.getPosition().getHeight() - heightLeft;
        TooltipMakerAPI tooltipFirst =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(), heightLeft, false);
        tooltipFirst.setParaFont(Fonts.ORBITRON_16);
        tooltipFirst
                .addPara(
                        "System Structures",
                        AshMisc.getClaimingFaction(currStarSystem.getCenter()).getBaseUIColor(),
                        17f)
                .setAlignment(Alignment.MID);
        if (struct == null) {
            struct =
                    new StarSystemStructuresUI(
                            contentPanel.getPosition().getWidth(), heightLeft, currStarSystem);
        }
        struct.setSystem(currStarSystem);
        tooltipFirst.addCustom(struct.getMainPanel(), 5f);
        contentPanel.addUIElement(tooltipFirst).inTL(0, startingY);
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
