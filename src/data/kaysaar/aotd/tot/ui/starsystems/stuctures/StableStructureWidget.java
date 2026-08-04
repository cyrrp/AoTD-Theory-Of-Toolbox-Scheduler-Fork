package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.ui.starsystems.components.ButtonWithImageComponent;
import java.awt.*;
import java.util.List;

public class StableStructureWidget implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    SectorEntityToken token;
    UILinesRenderer renderer;
    public static float width = 225;
    public static float height = 150;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public StableStructureWidget(SectorEntityToken token) {
        this.token = token;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        renderer = new UILinesRenderer(0f);
        createUI();
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
        ButtonWithImageComponent viewer =
                new ButtonWithImageComponent(
                        contentPanel.getPosition().getWidth(),
                        125,
                        token.getCustomInteractionDialogImageVisual().getSpriteName()) {
                    @Override
                    public void performActionOnClick(boolean isRightClick) {
                        super.performActionOnClick(isRightClick);
                    }
                };
        viewer.setEnableRightClick(false);
        TooltipMakerAPI tooltip =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight(),
                        false);
        Color c = Misc.getGrayColor();
        if (token.getFaction() != null) {
            c = token.getFaction().getBaseUIColor();
        }
        tooltip.addPara(token.getName(), c, 0f);
        tooltip.addCustom(viewer.getComponentPanel(), 2f);
        renderer.setBoxColor(c);
        renderer.setPanel(viewer.getComponentPanel());
        contentPanel.addUIElement(tooltip).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {
        renderer.render(alphaMult);
    }

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
