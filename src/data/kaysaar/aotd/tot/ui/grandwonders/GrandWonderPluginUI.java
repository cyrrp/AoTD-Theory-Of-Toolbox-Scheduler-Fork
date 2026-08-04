package data.kaysaar.aotd.tot.ui.grandwonders;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.industries.AoTDConstructionSite;
import java.util.List;

public class GrandWonderPluginUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    AoTDConstructionSite site;
    MarketAPI market;
    GrandWonderListUI listUI;
    GrandWonderAPI currChosen;
    CurrentGrantWonderView view;

    public GrandWonderPluginUI(float width, float height, AoTDConstructionSite site) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.market = site.getMarket();
        this.site = site;
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
        if (listUI != null) {
            listUI.createUI();
            contentPanel.addComponent(listUI.getMainPanel()).inTL(0, 0);
        } else {
            listUI = new GrandWonderListUI(400, contentPanel.getPosition().getHeight(), this);

            contentPanel.addComponent(listUI.getMainPanel()).inTL(0, 0);
        }
        if (currChosen != null) {
            view =
                    new CurrentGrantWonderView(
                            currChosen,
                            contentPanel.getPosition().getWidth() - 405,
                            contentPanel.getPosition().getHeight() - 60);
            contentPanel.addComponent(view.getMainPanel()).inTL(405, 0);
        }
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public GrandWonderAPI getCurrChosen() {
        return currChosen;
    }

    @Override
    public void clearUI() {}

    public void setCurrChosen(GrandWonderAPI currChosen) {
        this.currChosen = currChosen;
        createUI();
    }

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
