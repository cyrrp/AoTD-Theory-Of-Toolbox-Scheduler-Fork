package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import static data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityUITable.sortByState;

import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityTableDropDownButton;
import java.awt.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDDetailedCommodityUITable extends UITableImpl {
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();
    public static float seperation = 1f;
    float currYPos = 0;
    FactionAPI factionAPI;

    public AoTDDetailedCommodityUITable(
            float width,
            float height,
            boolean doesHaveScroller,
            float xCord,
            float yCord,
            boolean isProduction,
            MarketAPI mainMarket,
            String commodityId) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.isProduction = isProduction;
        this.factionAPI = mainMarket.getFaction();
        this.commodityId = commodityId;
        if (dropDownButtons.isEmpty()) {
            for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDCommodityOnMarket commodity =
                        AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI, commodityId);
                if (isProduction
                        && commodity.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0) {
                    AoTDCommodityTableDropDownButton bt =
                            new AoTDCommodityTableDropDownButton(
                                    this, width - 1, 26, 0, 0, true, commodity, isProduction);
                    dropDownButtons.add(bt);

                } else if (!isProduction
                        && commodity.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0) {
                    AoTDCommodityTableDropDownButton bt =
                            new AoTDCommodityTableDropDownButton(
                                    this, width - 1, 26, 0, 0, true, commodity, isProduction);
                    dropDownButtons.add(bt);
                }
            }
        }
    }

    public AoTDDetailedCommodityUITable(
            float width,
            float height,
            boolean doesHaveScroller,
            float xCord,
            float yCord,
            boolean isProduction,
            FactionAPI faction,
            String commodityId) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.isProduction = isProduction;
        this.factionAPI = faction;
        this.commodityId = commodityId;
        if (dropDownButtons.isEmpty()) {
            for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
                AoTDCommodityOnMarket commodity =
                        AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI, commodityId);
                if (isProduction
                        && commodity.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0) {
                    AoTDCommodityTableDropDownButton bt =
                            new AoTDCommodityTableDropDownButton(
                                    this, width - 1, 26, 0, 0, true, commodity, isProduction);
                    dropDownButtons.add(bt);

                } else if (!isProduction
                        && commodity.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0) {
                    AoTDCommodityTableDropDownButton bt =
                            new AoTDCommodityTableDropDownButton(
                                    this, width - 1, 26, 0, 0, true, commodity, isProduction);
                    dropDownButtons.add(bt);
                }
            }
        }
    }

    @Override
    public void createTable() {
        super.createTable();
        tooltipOfImpl.addSpacer(0f).getPosition().inTL(0, 0);
        for (DropDownButton dropDownButton : dropDownButtons) {
            dropDownButton.resetUI();
            dropDownButton.createUI();
            tooltipOfImpl.addCustom(dropDownButton.getPanelOfImpl(), 5f);
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

    public void setCommodityId(String commodityId) {
        this.commodityId = commodityId;
        for (DropDownButton dropDownButton : dropDownButtons) {
            dropDownButton.clearUI();
        }
        dropDownButtons.clear();
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            AoTDCommodityOnMarket commodity =
                    AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI, commodityId);
            if (isProduction && commodity.getSupplyDemandData().getTotalRawUnitsFromSupply() > 0) {
                AoTDCommodityTableDropDownButton bt =
                        new AoTDCommodityTableDropDownButton(
                                this, width - 1, 26, 0, 0, true, commodity, isProduction);
                dropDownButtons.add(bt);

            } else if (!isProduction
                    && commodity.getSupplyDemandData().getTotalRawUnitsFromDemand() > 0) {
                AoTDCommodityTableDropDownButton bt =
                        new AoTDCommodityTableDropDownButton(
                                this, width - 1, 26, 0, 0, true, commodity, isProduction);
                dropDownButtons.add(bt);
            }
        }
        this.recreateTable();
    }

    String commodityId;

    static {
        widthMap.put("colony", 210);
        widthMap.put("size", 60);
        widthMap.put("faction", 150);
        widthMap.put("quantity", 100);
        widthMap.put("excdef", 90);
        widthMap.put("acc", 100);
        widthMap.put("share", 90);
    }

    public static float getWidth() {
        float width = 0;
        for (Integer value : widthMap.values()) {
            width += value;
        }
        return width + (seperation * (widthMap.size() - 1));
    }

    public ButtonAPI colony, size, faction, quantity, excdef, share, lastCheckedState, acc;

    public static int getStartingX(String id) {
        int x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {

            if (id.equals(value.getKey())) {
                break;
            }
            x += (int) (value.getValue() + seperation);
        }
        return x;
    }

    boolean isProduction = true;

    @Override
    public void clearUI() {
        this.dropDownButtons.clear();
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
        handleSortButton(colony, Comparator.comparing(o -> o.commodity.getMarket().getName()));

        handleSortButton(size, Comparator.comparingInt(o -> o.commodity.getMarket().getSize()));
        handleSortButton(
                acc,
                Comparator.comparingDouble(
                        o -> o.commodity.getMarket().getAccessibilityMod().computeEffective(0f)));
        handleSortButton(
                faction,
                Comparator.comparing(x -> x.commodity.getMarket().getFaction().getDisplayName()));
        handleSortButton(
                quantity,
                Comparator.comparing(
                        x -> {
                            int supply =
                                    x.commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
                            int demand =
                                    x.commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
                            if (isProduction) {
                                return supply;
                            }
                            return demand;
                        }));
        handleSortButton(
                excdef,
                Comparator.comparing(
                        x -> {
                            int def = x.commodity.getDeficitQuantity();
                            int exc = x.commodity.getExcessQuantity();
                            if (isProduction) {
                                return exc;
                            }
                            return def;
                        }));
        handleSortButton(
                share,
                Comparator.comparing(
                        x -> {
                            float prod =
                                    x.commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
                            float total =
                                    AoTDSectorProductionDemandDataUtils
                                            .getTotalProductionFromSector(
                                                    x.commodity.getSpec().getId());
                            return prod / total;
                        }));
    }

    @Override
    public void createSections() {
        Color base = factionAPI.getBaseUIColor();
        Color bg = factionAPI.getDarkUIColor();
        Color bright = factionAPI.getBrightUIColor();
        String btName = "Production";
        String btExcName = "Excess";
        if (!isProduction) {
            btName = "Import";
            btExcName = "Deficit";
        }
        colony =
                tooltipOfButtons.addAreaCheckbox(
                        "Colony",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("colony"),
                        20,
                        0f);
        size =
                tooltipOfButtons.addAreaCheckbox(
                        "Size",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("size"),
                        20,
                        0f);
        faction =
                tooltipOfButtons.addAreaCheckbox(
                        "Faction",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("faction"),
                        20,
                        0f);
        quantity =
                tooltipOfButtons.addAreaCheckbox(
                        btName,
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("quantity"),
                        20,
                        0f);
        excdef =
                tooltipOfButtons.addAreaCheckbox(
                        btExcName,
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("excdef"),
                        20,
                        0f);
        acc =
                tooltipOfButtons.addAreaCheckbox(
                        "Accessibility",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("acc"),
                        20,
                        0f);
        share =
                tooltipOfButtons.addAreaCheckbox(
                        "Mkt.Share",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("share"),
                        20,
                        0f);
        colony.getPosition().inTL(seperation, 0);
        size.getPosition().rightOfMid(colony, seperation);
        faction.getPosition().rightOfMid(size, seperation);
        quantity.getPosition().rightOfMid(faction, seperation);
        excdef.getPosition().rightOfMid(quantity, seperation);
        acc.getPosition().rightOfMid(excdef, seperation);
        share.getPosition().rightOfMid(acc, seperation);
        if (!isProduction) {
            share.setClickable(false);
        }
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
        lastCheckedState = colony;
    }
}
