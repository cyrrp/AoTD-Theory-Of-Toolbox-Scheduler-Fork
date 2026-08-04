package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

public class ContractCreatorPlugin implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;
    ContractListTypePlugin list;
    ContractCreatorDetailsPlugin details;

    public ContractCreatorDetailsPlugin getExplainSection() {
        return details;
    }

    public ContractListTypePlugin getList() {
        return list;
    }

    public ContractCreatorPlugin(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            contentPanel.removeComponent(list.getMainPanel());
            contentPanel.removeComponent(details.getMainPanel());
            list = null;
            details = null;
            mainPanel.removeComponent(contentPanel);
        }
        float width = mainPanel.getPosition().getWidth();
        float height = mainPanel.getPosition().getHeight();
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        list = new ContractListTypePlugin(230f, height);
        details = new ContractCreatorDetailsPlugin(width - 230f - 18f, height);
        contentPanel.addComponent(list.getMainPanel()).inTL(0, 0);
        contentPanel.addComponent(details.getMainPanel()).inTL(240, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {
        list.clearUI();
        details.clearUI();
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (list != null && list.needsToUpdateUI) {
            list.setNeedsToUpdateUI(false);
            details.setId((String) list.chosen.getCustomData());
            details.createUI();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
