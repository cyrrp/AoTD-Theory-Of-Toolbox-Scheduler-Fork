package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.HoldingsUtilis;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class WarehouseHoldingTable extends UITableImpl {
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();
    public ButtonAPI buttonName, buttonData, buttonIncome, buttonAdmin;
    public MarketAPI currentlyChosenMarket;

    static {
        widthMap.put("name", 180);
        widthMap.put("location", 140);
        widthMap.put("data", 340);
        widthMap.put("upkeep", 100);
    }

    float currYPos = 0;
    private ButtonAPI lastCheckedState;

    public static void reDestributeAdditionalWidth(float additionalWidth) {
        if (additionalWidth <= 0) {
            return;
        }

        int remaining = (int) additionalWidth;

        // 2. Income gets up to 20px
        if (remaining > 0 && widthMap.containsKey("upkeep")) {
            int add = Math.min(20, remaining);
            widthMap.put("upkeep", widthMap.get("upkeep") + add);
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
        if (buttonAdmin.isChecked()) {
            buttonAdmin.setChecked(false);
            lastCheckedState = buttonAdmin;
            SortingState state = (SortingState) buttonAdmin.getCustomData();
            state = changeState(state);
            boolean ascending = false;
            if (state == SortingState.ASCENDING) {
                ascending = true;
            }
            HoldingsUtilis.sortDropDownButtonsIncome(dropDownButtons, ascending);
            buttonAdmin.setCustomData(state);
            this.recreateTable();
        }
        dropDownButtons.forEach(
                x -> {
                    if (x.mainButton.buttonData instanceof MarketAPI market) {
                        if (currentlyChosenMarket != null
                                && market.getId().equals(currentlyChosenMarket.getId())) {
                            x.mainButton.mainButton.highlight();
                        } else {
                            x.mainButton.mainButton.unhighlight();
                        }
                    }
                });
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

    public WarehouseHoldingTable(
            float width, float height, boolean doesHaveScroller, float xCord, float yCord) {
        super(width, height, doesHaveScroller, xCord, yCord);
        if (dropDownButtons.isEmpty()) {
            ArrayList<MarketAPI> systems = HoldingsUtilis.getStorageMarkets();
            for (MarketAPI system : systems) {
                WarehouseDropDown button =
                        new WarehouseDropDown(this, width - 13, 75, 0, 0, system);
                dropDownButtons.add(button);
            }
            HoldingsUtilis.sortDropDownButtonsIncome(dropDownButtons, false);
        }
    }

    @Override
    public void createTable() {
        super.createTable();
        tooltipOfImpl.addSpacer(0f).getPosition().inTL(-4, 0);
        for (DropDownButton dropDownButton : dropDownButtons) {
            dropDownButton.resetUI();
            dropDownButton.createUI();
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
                        "Location",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("location"),
                        20,
                        0f);
        buttonIncome =
                tooltipOfButtons.addAreaCheckbox(
                        "Data",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("data"),
                        20,
                        0f);
        buttonAdmin =
                tooltipOfButtons.addAreaCheckbox(
                        "Upkeep",
                        SortingState.DESCENDING,
                        base,
                        bg,
                        bright,
                        widthMap.get("upkeep"),
                        20,
                        0f);
        buttonName.getPosition().inTL(0, 0);
        buttonData.getPosition().rightOfMid(buttonName, 1);
        buttonIncome.getPosition().rightOfMid(buttonData, 1);
        buttonAdmin.getPosition().rightOfMid(buttonIncome, 1);
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
        lastCheckedState = buttonName;
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if (buttonPressed instanceof WarehouseCustomButton customButton) {
            this.currentlyChosenMarket = customButton.getMarket();
        }
    }
}
