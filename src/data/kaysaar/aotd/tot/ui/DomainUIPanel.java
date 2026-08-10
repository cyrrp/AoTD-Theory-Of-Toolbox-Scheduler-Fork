package data.kaysaar.aotd.tot.ui;

import ashlib.data.plugins.coreui.CommandTabMemoryManager;
import ashlib.data.plugins.coreui.CommandTabTracker;
import ashlib.data.plugins.coreui.CommandUIPlugin;
import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.graphics.util.Fader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.ui.core.EconomyTabListener;
import data.kaysaar.aotd.tot.ui.economy.EconomyCommodityData;
import data.kaysaar.aotd.tot.ui.economy.EconomyFactionIncome;
import data.kaysaar.aotd.tot.ui.economy.EconomyTradeDealsData;
import data.kaysaar.aotd.tot.ui.starsystems.StarSystemHoldingsUI;
import data.kaysaar.aotd.tot.ui.warehouses.WarehouseSectionUI;
import java.awt.*;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;

public class DomainUIPanel extends CommandUIPlugin {
    LazyUIPanel starSystemAndPlanetUI;
    Object original;
    LazyUIPanel economyCommodityData;
    LazyUIPanel economyTradeDealsData;
    LazyUIPanel factionIncome;
    LazyUIPanel warehouseSectionUI;
    public static boolean sentSignalForUpdate = false;

    public DomainUIPanel(float width, float height) {
        super(width, height);
    }

    @Override
    public boolean doesPlayCustomSound() {
        return false;
    }

    @Override
    public boolean doesPlayCustomSoundWhenEnteredEntireTab() {
        return false;
    }

    @Override
    public String getTabStateId() {
        return "domain";
    }

    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public void init(String panelToShowcase, Object data) {
        original = ReflectionUtilis.invokeMethodWithAutoProjection("getColoniesPanel", data);
        this.panelForPlugins =
                mainPanel.createCustomPanel(
                        mainPanel.getPosition().getWidth(),
                        mainPanel.getPosition().getHeight() - 45,
                        null);
        if (!AshMisc.isStringValid(panelToShowcase)) {
            panelToShowcase = "star systems & colonies";
        }
        currentlyChosen = null;
        createButtonsAndMainPanels();
        for (Map.Entry<ButtonAPI, CustomPanelAPI> buttons : panelMap.entrySet()) {
            if (buttons.getKey().getText().toLowerCase().contains(panelToShowcase)) {
                currentlyChosen = buttons.getKey();
                break;
            }
        }
        if (currentlyChosen == null) {
            for (ButtonAPI button : panelMap.keySet()) {
                if (button.getText().equalsIgnoreCase("Star Systems & Colonies")) {
                    currentlyChosen = button;
                    break;
                }
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

    public void clearUI(boolean clearMusic) {
        panelMap.clear();
        mainPanel.removeComponent(panelForPlugins);
        starSystemAndPlanetUI = null;
        economyCommodityData = null;
        economyTradeDealsData = null;
        factionIncome = null;
        warehouseSectionUI = null;
        currentlyChosen = null;
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (CommandTabTracker.lockedState) {
                if (event.isConsumed()) continue;
                if (event.getEventValue() == Keyboard.KEY_ESCAPE && !event.isMouseEvent()) {
                    event.consume();
                }
            }
        }
    }

    @Override
    public void buttonPressed(Object buttonId) {}

    @Override
    public void advance(float amount) {
        createPanelContentIfNeeded(currentlyChosen);
        super.advance(amount);
    }

    @Override
    public void playSound(Object data) {}

    public void pauseSound() {}

    public void createButtonsAndMainPanels() {
        ButtonAPI research, customProd;
        this.buttonPanel =
                this.mainPanel.createCustomPanel(mainPanel.getPosition().getWidth(), 25, null);
        UILinesRenderer renderer = new UILinesRenderer(0f);
        CustomPanelAPI panelHelper = this.buttonPanel.createCustomPanel(490, 0.5f, renderer);
        ButtonAPI tradeData, commData;

        TooltipMakerAPI buttonTooltip =
                buttonPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        Color base, bg;
        base = Global.getSector().getPlayerFaction().getBaseUIColor();
        bg = Global.getSector().getPlayerFaction().getDarkUIColor();
        customProd =
                buttonTooltip.addButton(
                        "Star Systems & Colonies",
                        null,
                        base,
                        bg,
                        Alignment.MID,
                        CutStyle.TOP,
                        205,
                        20,
                        0f);
        research =
                buttonTooltip.addButton(
                        "Warehouses", null, base, bg, Alignment.MID, CutStyle.TOP, 150, 20, 0f);

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

        commData.setShortcut(Keyboard.KEY_T, false);
        tradeData.setShortcut(Keyboard.KEY_Y, false);

        customProd.setShortcut(Keyboard.KEY_R, false);
        customProd.getPosition().inTL(0, 0);

        research.setShortcut(Keyboard.KEY_U, false);
        research.getPosition().rightOfMid(customProd, 1);
        commData.getPosition().rightOfMid(research, 1);
        tradeData.getPosition().rightOfMid(commData, 1);

        insertStarSystemPanel(customProd);
        insertCommDataPanel(commData);
        insertTradeDataPanel(tradeData);
        insertWarehouseUI(research);
        buttonPanel.addUIElement(buttonTooltip).inTL(0, 0);
        buttonPanel.addComponent(panelHelper).inTL(0, 20);
        mainPanel.addComponent(buttonPanel).inTL(0, 10);
    }

    @Override
    public void resetCurrentPlugin(ButtonAPI newButton) {
        super.resetCurrentPlugin(newButton);
        CommandTabMemoryManager.getInstance()
                .getTabStates()
                .put(getTabStateId(), newButton.getText().toLowerCase());
        CustomPanelAPI selectedPanel = panelMap.get(newButton);
        if (selectedPanel == null) {
            return;
        }
        if (selectedPanel.getPlugin() instanceof LazyUIPanel lazyPanel) {
            if (!lazyPanel.isInitialized()) {
                lazyPanel.createUI();
                return;
            }
            ExtendedUIPanelPlugin plugin = lazyPanel.getDelegate();

            if (plugin instanceof StarSystemHoldingsUI holdingsUI) {
                holdingsUI.table.dropDownButtons.forEach(
                        x -> {
                            x.resetUI();
                            x.createUI();
                        });
                return;
            }
            plugin.createUI();
        }
    }

    private void createPanelContentIfNeeded(ButtonAPI button) {
        CustomPanelAPI selectedPanel = panelMap.get(button);
        if (selectedPanel != null && selectedPanel.getPlugin() instanceof LazyUIPanel lazyPanel) {
            if (!lazyPanel.isInitialized()) {
                lazyPanel.createUI();
            }
        }
    }

    private void insertStarSystemPanel(ButtonAPI tiedButton) {
        if (starSystemAndPlanetUI == null) {
            float width = panelForPlugins.getPosition().getWidth() - 5;
            float height = panelForPlugins.getPosition().getHeight();
            starSystemAndPlanetUI =
                    new LazyUIPanel(
                            width, height, () -> new StarSystemHoldingsUI(width, height, original));
        }

        panelMap.put(tiedButton, starSystemAndPlanetUI.getMainPanel());
    }

    private void insertWarehouseUI(ButtonAPI tiedButton) {
        if (warehouseSectionUI == null) {
            float width = panelForPlugins.getPosition().getWidth() - 5;
            float height = panelForPlugins.getPosition().getHeight();
            warehouseSectionUI =
                    new LazyUIPanel(width, height, () -> new WarehouseSectionUI(width, height));
        }

        panelMap.put(tiedButton, warehouseSectionUI.getMainPanel());
    }

    private void insertCommDataPanel(ButtonAPI tiedButton) {
        if (economyCommodityData == null) {
            float width = EconomyTabListener.WIDTH;
            float height = EconomyTabListener.HEIGHT;
            economyCommodityData =
                    new LazyUIPanel(width, height, () -> new EconomyCommodityData(width, height));
        }

        panelMap.put(tiedButton, economyCommodityData.getMainPanel());
    }

    private void insertTradeDataPanel(ButtonAPI tiedButton) {
        if (economyTradeDealsData == null) {
            float width = EconomyTabListener.WIDTH;
            float height = EconomyTabListener.HEIGHT;
            economyTradeDealsData =
                    new LazyUIPanel(width, height, () -> new EconomyTradeDealsData(width, height));
        }

        panelMap.put(tiedButton, economyTradeDealsData.getMainPanel());
    }

    private void insertFactionIncome(ButtonAPI tiedButton) {
        if (factionIncome == null) {
            float width = EconomyTabListener.WIDTH;
            float height = EconomyTabListener.HEIGHT;
            factionIncome =
                    new LazyUIPanel(width, height, () -> new EconomyFactionIncome(width, height));
        }

        panelMap.put(tiedButton, factionIncome.getMainPanel());
    }

    public void playSound(ButtonAPI button) {}
}
