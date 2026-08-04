package data.kaysaar.aotd.tot.ui.starsystems;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.ui.starsystems.components.StarSystemHoldingDropDown;
import data.kaysaar.aotd.tot.ui.starsystems.components.StarSystemHoldingTable;
import java.util.List;
import org.lwjgl.input.Keyboard;

public class StarSystemHoldingsUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;

    public StarSystemHoldingTable table;
    CurrentStarSystemTab currSystem;
    Object originalPanel;
    ButtonAPI adminButton;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public StarSystemHoldingsUI(float width, float height, Object originalPanel) {
        this.originalPanel = originalPanel;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);

        createUI();
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            contentPanel.removeComponent(mainPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);

        if (table != null) {
            table.recreateTable();
        } else {
            if (contentPanel.getPosition().getWidth() - StarSystemHoldingTable.getWidth() - 18
                    > 765) {
                float additional =
                        contentPanel.getPosition().getWidth()
                                - StarSystemHoldingTable.getWidth()
                                - 18
                                - 765;
                StarSystemHoldingTable.reDestributeAdditionalWidth(additional);
            }
            CustomPanelAPI panel =
                    Global.getSettings()
                            .createCustom(
                                    StarSystemHoldingTable.getWidth() + 13,
                                    contentPanel.getPosition().getHeight() - 55,
                                    null);
            table =
                    new StarSystemHoldingTable(
                            panel.getPosition().getWidth(),
                            panel.getPosition().getHeight(),
                            panel,
                            true,
                            0,
                            0,
                            originalPanel);
            if (Global.getSector()
                    .getPlayerMemoryWithoutUpdate()
                    .contains("$aotd_tab_holdings_star_id")) {
                String id =
                        Global.getSector()
                                .getPlayerMemoryWithoutUpdate()
                                .getString("$aotd_tab_holdings_star_id");
                for (DropDownButton dropDownButton : table.dropDownButtons) {
                    if (dropDownButton instanceof StarSystemHoldingDropDown bt) {
                        if (bt.getStarSystem().getId().equals(id)) {
                            table.currSystem = bt.getStarSystem();
                        }
                    }
                }
            }
            table.createSections();
            table.createTable();
        }
        if (currSystem == null) {
            currSystem =
                    new CurrentStarSystemTab(
                            Math.min(
                                    contentPanel.getPosition().getWidth()
                                            - StarSystemHoldingTable.getWidth()
                                            - 18,
                                    750),
                            contentPanel.getPosition().getHeight() - 45);
        }
        contentPanel
                .addComponent(currSystem.getMainPanel())
                .inTL(
                        contentPanel.getPosition().getWidth()
                                - currSystem.getMainPanel().getPosition().getWidth()
                                - 15,
                        0);
        contentPanel.addComponent(table.mainPanel).inTL(-10, 0);
        TooltipMakerAPI tlButton =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 30, false);
        adminButton =
                tlButton.addButton(
                        "Manage Administrators",
                        originalPanel,
                        Misc.getBasePlayerColor(),
                        Misc.getDarkPlayerColor(),
                        Alignment.MID,
                        CutStyle.BOTTOM,
                        300,
                        25,
                        0f);
        adminButton.getPosition().inTL(0, 0);
        adminButton.setShortcut(Keyboard.KEY_W, true);
        contentPanel.addUIElement(tlButton).inTL(0, contentPanel.getPosition().getHeight() - 23);
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
        if (table != null) {
            table.render(alphaMult);
        }
    }

    @Override
    public void advance(float amount) {
        if (table != null) {
            table.advance(amount);
            if (table.currSystem != null) {
                if (currSystem.currStarSystem == null) {
                    currSystem.setCurrStarSystem(table.currSystem);
                    Global.getSector()
                            .getPlayerMemoryWithoutUpdate()
                            .set("$aotd_tab_holdings_star_id", table.currSystem.getId());
                } else if (!currSystem.currStarSystem.getId().equals(table.currSystem.getId())) {
                    currSystem.setCurrStarSystem(table.currSystem);
                    Global.getSector()
                            .getPlayerMemoryWithoutUpdate()
                            .set("$aotd_tab_holdings_star_id", table.currSystem.getId());
                }
            }
            if (table.currentlyChosenMarket != null) {
                MarketAPI copy = table.currentlyChosenMarket;
                table.currentlyChosenMarket = null;
                ReflectionUtilis.invokeMethodWithAutoProjection(
                        "showMarketDetail", originalPanel, copy);
            }
        }
        if (adminButton != null && adminButton.isChecked()) {
            adminButton.setChecked(false);
            ButtonAPI button = null;
            for (UIComponentAPI componentAPI :
                    ReflectionUtilis.getChildrenCopy((UIPanelAPI) originalPanel)) {
                if (componentAPI instanceof ButtonAPI bt) {
                    button = bt;
                }
            }
            if (button != null) {
                ReflectionUtilis.invokeMethodWithAutoProjection(
                        "actionPerformed", originalPanel, null, button);
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
