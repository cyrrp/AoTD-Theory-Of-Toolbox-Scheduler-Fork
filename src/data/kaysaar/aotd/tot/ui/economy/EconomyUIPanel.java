package data.kaysaar.aotd.tot.ui.economy;

import ashlib.data.plugins.coreui.CommandTabMemoryManager;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.ui.core.EconomyTabListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class EconomyUIPanel extends CommandUIPlugin {
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    EconomyCommodityData economyCommodityData;
    EconomyTradeDealsData economyTradeDealsData;
    EconomyFactionIncome factionIncome;

    public EconomyUIPanel(float width, float height) {
        super(width, height);
    }

    public HashMap<ButtonAPI, CustomPanelAPI> getPanelMap() {
        return panelMap;
    }

    @Override
    public void init(String panelToShowcase, Object data) {
        this.panelForPlugins =
                mainPanel.createCustomPanel(
                        mainPanel.getPosition().getWidth(),
                        mainPanel.getPosition().getHeight() - 45,
                        null);
        createButtonsAndMainPanels();
        if (panelToShowcase == null) {
            panelToShowcase = "commodity data";
        }
        for (Map.Entry<ButtonAPI, CustomPanelAPI> buttons : panelMap.entrySet()) {
            if (buttons.getKey().getText().toLowerCase().contains(panelToShowcase)) {
                currentlyChosen = buttons.getKey();
                break;
            }
        }
        for (CustomPanelAPI value : panelMap.values()) {
            panelForPlugins.addComponent(value).inTL(0, 0);
        }
        if (currentlyChosen != null) {
            for (Map.Entry<ButtonAPI, CustomPanelAPI> entry : panelMap.entrySet()) {
                Fader fader =
                        (Fader)
                                ReflectionUtilis.invokeMethodWithAutoProjection(
                                        "getFader", entry.getValue());
                if (entry.getKey().equals(currentlyChosen)) {
                    fader.forceIn();
                } else {
                    fader.forceOut();
                }
            }
        }
        this.mainPanel.addComponent(panelForPlugins).inTL(0, 35);
    }

    @Override
    public void advance(float amount) {

        for (Map.Entry<ButtonAPI, CustomPanelAPI> entry : panelMap.entrySet()) {
            entry.getKey().unhighlight();
            if (entry.getKey().isChecked()) {
                entry.getKey().setChecked(false);
                if (!entry.getKey().equals(currentlyChosen)) {
                    resetCurrentPlugin(entry.getKey());
                    CommandTabMemoryManager.getInstance()
                            .getTabStates()
                            .put(getTabStateId(), entry.getKey().getText().toLowerCase());
                }

                break;
            }
        }
        if (currentlyChosen != null) {
            currentlyChosen.highlight();
        }
    }

    @Override
    public void resetCurrentPlugin(ButtonAPI newButton) {
        super.resetCurrentPlugin(newButton);
        if (panelMap.get(newButton).getPlugin() instanceof ExtendedUIPanelPlugin plugin) {
            plugin.createUI();
        }
    }

    public void createButtonsAndMainPanels() {
        ButtonAPI tradeData, incomeData, commData, sp;
        this.buttonPanel =
                this.mainPanel.createCustomPanel(mainPanel.getPosition().getWidth(), 25, null);
        UILinesRenderer renderer = new UILinesRenderer(0f);
        CustomPanelAPI panelHelper = this.buttonPanel.createCustomPanel(490, 0.5f, renderer);
        //        renderer.setPanel(panelHelper);
        TooltipMakerAPI buttonTooltip =
                buttonPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        Color base, bg;
        base = Global.getSector().getPlayerFaction().getBaseUIColor();
        bg = Global.getSector().getPlayerFaction().getDarkUIColor();
        commData =
                buttonTooltip.addButton(
                        "Commodity Data", null, base, bg, Alignment.MID, CutStyle.TOP, 150, 20, 0f);
        tradeData =
                buttonTooltip.addButton(
                        "Trade Contracts",
                        null,
                        base,
                        bg,
                        Alignment.MID,
                        CutStyle.TOP,
                        150,
                        20,
                        0f);

        commData.setShortcut(Keyboard.KEY_R, false);
        tradeData.setShortcut(Keyboard.KEY_T, false);
        commData.getPosition().inTL(0, 0);
        tradeData.getPosition().rightOfMid(commData, 1);
        insertCommDataPanel(commData);
        insertTradeDataPanel(tradeData);

        buttonPanel.addUIElement(buttonTooltip).inTL(0, 0);
        buttonPanel.addComponent(panelHelper).inTL(0, 20);
        mainPanel.addComponent(buttonPanel).inTL(0, 10);
    }

    private void insertCommDataPanel(ButtonAPI tiedButton) {
        if (economyCommodityData == null) {
            economyCommodityData =
                    new EconomyCommodityData(EconomyTabListener.WIDTH, EconomyTabListener.HEIGHT);
        }

        panelMap.put(tiedButton, economyCommodityData.getMainPanel());
    }

    private void insertTradeDataPanel(ButtonAPI tiedButton) {
        if (economyTradeDealsData == null) {
            economyTradeDealsData =
                    new EconomyTradeDealsData(EconomyTabListener.WIDTH, EconomyTabListener.HEIGHT);
        }

        panelMap.put(tiedButton, economyTradeDealsData.getMainPanel());
    }

    private void insertFactionIncome(ButtonAPI tiedButton) {
        if (factionIncome == null) {
            factionIncome =
                    new EconomyFactionIncome(EconomyTabListener.WIDTH, EconomyTabListener.HEIGHT);
        }

        panelMap.put(tiedButton, factionIncome.getMainPanel());
    }
}
