package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.HoldingsUtilis;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class StarSystemHoldingTable extends UITableImpl {
    public ButtonAPI buttonName, buttonData, buttonIncome, buttonAdmin;
    float currYPos = 0;
    ButtonAPI lastCheckedState;
    public MarketAPI currentlyChosenMarket;
    UILinesRenderer renderer = new UILinesRenderer(0f);
    public StarSystemAPI currSystem;
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();

    static {
        widthMap.put("name", 180);
        widthMap.put("data", 355);
        widthMap.put("income", 100);
        widthMap.put("admin", 75);
    }

    public static void reDestributeAdditionalWidth(float additionalWidth) {
        if (additionalWidth <= 0) {
            return;
        }

        int remaining = (int) additionalWidth;

        // 1. Name gets up to 30px
        if (remaining > 0 && widthMap.containsKey("name")) {
            int add = Math.min(30, remaining);
            widthMap.put("name", widthMap.get("name") + add);
            remaining -= add;
        }

        // 2. Income gets up to 20px
        if (remaining > 0 && widthMap.containsKey("income")) {
            int add = Math.min(20, remaining);
            widthMap.put("income", widthMap.get("income") + add);
            remaining -= add;
        }

        // 3. Rest goes to data
        if (remaining > 0 && widthMap.containsKey("data")) {
            widthMap.put("data", widthMap.get("data") + remaining);
        }
    }

    public static int getWidth() {
        int width = 0;
        for (Integer value : widthMap.values()) {
            width += value + 1;
        }
        width -= 1;
        return width;
    }

    public static int getStartingX(String id) {
        int x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {

            if (id.equals(value.getKey())) {
                break;
            }
            x += value.getValue() + 1;
        }
        return x;
    }

    public StarSystemHoldingTable(
            float width,
            float height,
            CustomPanelAPI panelToPlace,
            boolean doesHaveScroller,
            float xCord,
            float yCord,
            Object originalPanel) {
        super(width, height, panelToPlace, doesHaveScroller, xCord, yCord);
        renderer.setPanel(this.mainPanel);
        if (dropDownButtons.isEmpty()) {
            ArrayList<StarSystemAPI> systems = HoldingsUtilis.getSystemsWithPlayerFactionColonies();
            for (StarSystemAPI system : systems) {
                StarSystemHoldingDropDown button =
                        new StarSystemHoldingDropDown(
                                this,
                                width - 13,
                                75,
                                0,
                                0,
                                false,
                                system,
                                HoldingsUtilis.getFactionMarketsInSystem(
                                        Global.getSector().getPlayerFaction(), system),
                                originalPanel);
                dropDownButtons.add(button);
            }
            HoldingsUtilis.sortDropDownButtonsIncome(dropDownButtons, false);
        }
    }

    @Override
    public void clearTable() {
        super.clearTable();
    }

    @Override
    public void clearUI() {
        super.clearUI();
    }

    @Override
    public void createSections() {
        Color base = Misc.getBasePlayerColor();
        Color bg = Misc.getDarkPlayerColor();
        Color bright = Misc.getBrightPlayerColor();
        buttonName =
                tooltipOfButtons.addAreaCheckbox(
                        "Name",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("name"),
                        20,
                        0f);
        buttonData =
                tooltipOfButtons.addAreaCheckbox(
                        "Data",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("data"),
                        20,
                        0f);
        buttonIncome =
                tooltipOfButtons.addAreaCheckbox(
                        "Income",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("income"),
                        20,
                        0f);
        buttonAdmin =
                tooltipOfButtons.addAreaCheckbox(
                        "Admin",
                        SortingState.DESCENDING,
                        base,
                        bg,
                        bright,
                        widthMap.get("admin"),
                        20,
                        0f);
        buttonName.getPosition().inTL(10, 0);
        buttonData.getPosition().inTL(buttonName.getPosition().getWidth() + 11, 0);
        float x =
                buttonName.getPosition().getWidth() + 11 + buttonData.getPosition().getWidth() + 1;
        buttonIncome.getPosition().inTL(x, 0);
        x += buttonIncome.getPosition().getWidth() + 1;
        buttonAdmin.getPosition().inTL(x, 0);
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
        lastCheckedState = buttonName;
    }

    @Override
    public void createTable() {
        super.createTable();
        for (DropDownButton dropDownButton : dropDownButtons) {
            StarSystemHoldingDropDown button = (StarSystemHoldingDropDown) dropDownButton;
            if (currSystem != null && !button.main.getId().equals(currSystem.getId())) {
                button.isDropped = false;
            } else if (currSystem != null && button.main.getId().equals(currSystem.getId())) {
                button.isDropped = true;
            }

            button.resetUI();
            button.createUI();
            tooltipOfImpl.addCustom(dropDownButton.getPanelOfImpl(), 2f);
        }

        panelToWorkWith.addUIElement(tooltipOfImpl).inTL(0, 0);
        if (tooltipOfImpl.getExternalScroller() != null) {
            if (currYPos + panelToWorkWith.getPosition().getHeight() - 2
                    >= tooltipOfImpl.getHeightSoFar()) {
                currYPos =
                        tooltipOfImpl.getHeightSoFar()
                                - panelToWorkWith.getPosition().getHeight()
                                + 2;
            }
            if (currYPos <= 0) {
                currYPos = 0;
            }
            tooltipOfImpl.getExternalScroller().setYOffset(currYPos);
        }

        mainPanel.addComponent(panelToWorkWith).inTL(0, 22);
    }

    private SortingState changeState(SortingState state) {
        if (state == SortingState.NON_INITIALIZED) {
            return SortingState.ASCENDING;
        }
        if (state == SortingState.ASCENDING) {
            return SortingState.DESCENDING;
        }
        if (state == SortingState.DESCENDING) {
            return SortingState.ASCENDING;
        }
        return SortingState.NON_INITIALIZED;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (tooltipOfImpl != null && tooltipOfImpl.getExternalScroller() != null) {
            currYPos = tooltipOfImpl.getExternalScroller().getYOffset();
        }
        if (buttonName.isChecked()) {
            buttonName.setChecked(false);
            lastCheckedState = buttonName;
            SortingState state = (SortingState) buttonName.getCustomData();
            state = changeState(state);
            boolean ascending = false;
            if (state == SortingState.ASCENDING) {
                ascending = true;
            }
            HoldingsUtilis.sortDropDownButtonsByName(dropDownButtons, ascending);
            buttonName.setCustomData(state);

            this.recreateTable();
        }
        if (buttonIncome.isChecked()) {
            buttonIncome.setChecked(false);
            lastCheckedState = buttonIncome;
            SortingState state = (SortingState) buttonIncome.getCustomData();
            state = changeState(state);
            boolean ascending = false;
            if (state == SortingState.ASCENDING) {
                ascending = true;
            }
            HoldingsUtilis.sortDropDownButtonsIncome(dropDownButtons, ascending);
            buttonIncome.setCustomData(state);
            this.recreateTable();
        }
        dropDownButtons.forEach(
                x -> {
                    x.buttons.forEach(
                            y -> {
                                if (y.buttonData instanceof MarketAPI market) {
                                    if (currentlyChosenMarket != null
                                            && market.getId()
                                                    .equals(currentlyChosenMarket.getId())) {
                                        y.mainButton.highlight();
                                    } else {
                                        y.mainButton.unhighlight();
                                    }
                                }
                            });
                });
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if (buttonPressed.buttonData instanceof StarSystemAPI systemAPI) {
            this.currSystem = systemAPI;
        }
        if (buttonPressed.buttonData instanceof MarketAPI market) {
            this.currentlyChosenMarket = market;
        }
    }

    @Override
    public void render(float alphaMult) {
        super.render(alphaMult);
    }
}
