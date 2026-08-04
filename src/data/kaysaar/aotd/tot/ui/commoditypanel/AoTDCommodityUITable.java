package data.kaysaar.aotd.tot.ui.commoditypanel;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.AoTDDetailedCommodityPanelContent;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.CommodityDetailDialog;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AoTDCommodityUITable extends UITableImpl {

    public static float width = 324;
    public ArrayList<AoTDCommodityOnMarket> commodities;
    public AoTDDetailedCommodityPanelContent parent;
    float currYPos = 0;
    public ButtonAPI buttonCommodity, buttonProd, buttonDemand, buttonImport, buttonExport;
    ButtonAPI lastCheckedState;
    MarketAPI market;
    boolean isInDialog = false;
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();

    static {
        widthMap.put("commodity", 90);
        widthMap.put("production", 83);
        widthMap.put("demand", 83);
        widthMap.put("import", 73);
        widthMap.put("deficit", 73);
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

    public static float getWidth() {
        float x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {
            x += value.getValue() + 1;
        }
        return x;
    }

    public AoTDCommodityUITable(
            float width,
            float height,
            boolean doesHaveScroller,
            float xCord,
            float yCord,
            ArrayList<AoTDCommodityOnMarket> commodities,
            MarketAPI market,
            boolean isInDialog,
            AoTDDetailedCommodityPanelContent parent) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.parent = parent;
        this.commodities = commodities;
        this.market = market;
        this.isInDialog = isInDialog;
        if (dropDownButtons.isEmpty()) {
            for (AoTDCommodityOnMarket commodity : commodities) {
                AoTDCommodityTableDropDownButton bt =
                        new AoTDCommodityTableDropDownButton(
                                this, width - 1, 40, 0, 0, false, commodity);
                dropDownButtons.add(bt);
            }
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
            AoTDCommodityTableDropDownButton bt = (AoTDCommodityTableDropDownButton) dropDownButton;
            tooltipOfImpl.addTooltipToPrevious(
                    new CommodityButtonOnHover(
                            bt.commodity.getSpec(), bt.commodity.getMarket(), isInDialog),
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);
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

    private void handleSortButton(
            ButtonAPI button, Comparator<AoTDCommodityTableDropDownButton> comparator) {
        if (button == null || comparator == null) return;
        if (!button.isChecked()) return;

        button.setChecked(false);

        SortingState current = (SortingState) button.getCustomData();
        SortingState newState = this.switchState(current);

        sortByState(dropDownButtons, newState, comparator);

        button.setCustomData(newState);
        recreateTable();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        handleSortButton(
                buttonCommodity, Comparator.comparing(o -> o.commodity.getSpec().getName()));

        handleSortButton(
                buttonProd,
                Comparator.comparingInt(
                        o -> o.commodity.getSupplyDemandData().getTotalRawUnitsFromSupply()));

        handleSortButton(
                buttonDemand,
                Comparator.comparing(
                        x -> x.commodity.getSupplyDemandData().getTotalRawUnitsFromDemand()));
        handleSortButton(
                buttonImport,
                Comparator.comparing(
                        x -> {
                            int supply =
                                    x.commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
                            int demand =
                                    x.commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
                            return supply - demand;
                        }));
        handleSortButton(
                buttonExport,
                Comparator.comparing(
                        x -> {
                            int def = x.commodity.getDeficitQuantity();
                            int exc = x.commodity.getExcessQuantity();
                            return exc - def;
                        }));
    }

    public static <T> void sortByState(List list, SortingState state, Comparator<T> comparator) {
        if (list == null || comparator == null || state == null) return;

        if (state == SortingState.ASCENDING) {
            list.sort(comparator);
        } else {
            list.sort(comparator.reversed());
        }
    }

    @Override
    public void createSections() {
        Color base = market.getFaction().getBaseUIColor();
        Color bg = market.getFaction().getDarkUIColor();
        Color bright = market.getFaction().getBrightUIColor();
        buttonCommodity =
                tooltipOfButtons.addAreaCheckbox(
                        "Commodity",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("commodity"),
                        20,
                        0f);
        buttonProd =
                tooltipOfButtons.addAreaCheckbox(
                        "Supply",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("production"),
                        20,
                        0f);
        buttonDemand =
                tooltipOfButtons.addAreaCheckbox(
                        "Demand",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("demand"),
                        20,
                        0f);
        buttonImport =
                tooltipOfButtons.addAreaCheckbox(
                        "Exp / Imp",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("import"),
                        20,
                        0f);
        buttonExport =
                tooltipOfButtons.addAreaCheckbox(
                        "Exc / Def",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("deficit"),
                        20,
                        0f);
        buttonCommodity.getPosition().inTL(1, 0);
        buttonProd.getPosition().inTL(buttonCommodity.getPosition().getWidth() + 1, 0);
        float x =
                buttonCommodity.getPosition().getWidth()
                        + 2
                        + buttonProd.getPosition().getWidth()
                        + 1;
        buttonDemand.getPosition().inTL(x, 0);
        x += buttonDemand.getPosition().getWidth() + 1;
        buttonImport.getPosition().inTL(x, 0);
        x += buttonImport.getPosition().getWidth() + 1;
        buttonExport.getPosition().inTL(x, 0);
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
        lastCheckedState = buttonCommodity;
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if (buttonPressed instanceof AoTDCommodityInfoButton infoButton) {
            if (!isInDialog) {
                AshMisc.initPopUpDialog(
                        new CommodityDetailDialog(market, infoButton.getData().getSpec().getId()),
                        1270,
                        665);
            } else if (parent != null) {
                parent.commodity = infoButton.getData().getId();
                parent.createUI();
            }
        }
    }
}
