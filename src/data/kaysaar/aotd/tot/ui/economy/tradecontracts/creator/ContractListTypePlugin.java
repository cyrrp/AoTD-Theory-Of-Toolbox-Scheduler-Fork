package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContractListTypePlugin implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI componentPanel;
    ArrayList<ButtonAPI> buttons = new ArrayList<>();
    ButtonAPI chosen;

    public ContractListTypePlugin(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public boolean needsToUpdateUI = false;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (componentPanel != null) {
            mainPanel.removeComponent(componentPanel);
        }
        componentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tooltipHeader =
                componentPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        tooltipHeader.addSectionHeading("Available Contracts", Alignment.MID, 0f);
        TooltipMakerAPI tooltipButton =
                componentPanel.createUIElement(
                        mainPanel.getPosition().getWidth(),
                        mainPanel.getPosition().getHeight() - 25,
                        true);
        componentPanel.addUIElement(tooltipHeader).inTL(0, 0);
        float opad = 0f;
        for (Map.Entry<String, PlayerContractCreatorAPI> entry :
                AoTDPlayerContractCreatorManager.contractCreators.entrySet()) {
            ButtonAPI button =
                    tooltipButton.addButton(
                            entry.getValue().getNameOfContract(),
                            entry.getValue().getBaseIdForContract(),
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            Alignment.MID,
                            CutStyle.NONE,
                            componentPanel.getPosition().getWidth() - 9,
                            40,
                            opad);
            int curr =
                    AoTDToolboxMisc.getAmountOfContractsOfSameType(
                            entry.getValue().getBaseIdForContract());
            if (curr >= entry.getValue().getMaxAmountOfConcurrentContracts()
                    && !entry.getValue().isContractUnlimited()) {
                button.setEnabled(false);
            }
            if (!entry.getValue().canUseContract()) {
                button.setEnabled(false);
            }
            TooltipMakerAPI.TooltipCreator creator =
                    entry.getValue().generateTooltipCreatorForButtonOnList(400);
            if (creator != null) {
                tooltipButton.addTooltipTo(
                        creator, button, TooltipMakerAPI.TooltipLocation.RIGHT, false);
            }
            buttons.add(button);
            opad = 5f;
        }
        componentPanel.addUIElement(tooltipButton).inTL(0, 22);

        mainPanel.addComponent(componentPanel).inTL(0, 0);
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    public void setNeedsToUpdateUI(boolean needsToUpdateUI) {
        this.needsToUpdateUI = needsToUpdateUI;
    }

    @Override
    public void render(float alphaMult) {
        for (ButtonAPI button : buttons) {
            button.unhighlight();
            if (button.isChecked()) {
                button.setChecked(false);
                needsToUpdateUI = true;
                if (chosen != null) {
                    chosen.unhighlight();
                }
                chosen = button;
                break;
            }
        }
        if (chosen != null) {
            chosen.highlight();
        }
    }

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}

    public void clearUI() {

        buttons.clear();
    }
}
