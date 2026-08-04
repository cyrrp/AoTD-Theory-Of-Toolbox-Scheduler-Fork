package data.kaysaar.aotd.tot.ui.grandwonders;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderTypeManager;
import java.util.List;

public class GrandWonderListUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    GrandWonderPluginUI pluginUI;

    public GrandWonderListUI(float width, float height, GrandWonderPluginUI plugin) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.pluginUI = plugin;
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
        TooltipMakerAPI tlHeader =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 20, false);
        tlHeader.addSectionHeading("Grand Wonder List", Alignment.MID, 0f);
        TooltipMakerAPI tlContent =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight() - 25,
                        true);
        tlContent.addSpacer(0f).getPosition().inTL(2, 0);
        for (GrandWonderAPI grandWonderAPI :
                GrandWonderTypeManager.getWondersVisibleForMarket(pluginUI.market)) {
            GrandWonderButtonComponent component =
                    new GrandWonderButtonComponent(
                            contentPanel.getPosition().getWidth() - 15,
                            54,
                            grandWonderAPI,
                            pluginUI.market);
            component.createUI();
            component.setListener(
                    new CustomButton.ButtonEventListener() {
                        @Override
                        public void onButtonClicked() {
                            pluginUI.setCurrChosen(component.getButtonData());
                        }
                    });
            tlContent.addCustom(component.getMainPanel(), 2f);
        }
        contentPanel.addUIElement(tlHeader).inTL(0, 0);
        contentPanel.addUIElement(tlContent).inTL(0, 25);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
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
