package data.kaysaar.aotd.tot.ui.economy.tradecontracts;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.DropDownButton;
import ashlib.data.plugins.ui.plugins.UITableImpl;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.popup.ContractDataPopUP;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class TradeContractUITable extends UITableImpl {
    public static LinkedHashMap<String, Integer> widthMap = new LinkedHashMap<>();
    public static float seperation = 1f;
    String currentlyChosenContract = null;
    boolean isContractBrowsingMode = false;

    static {
        widthMap.put("contractor", 220);
        widthMap.put("typeofcontract", 220);
        widthMap.put("commodities", 140);
        widthMap.put("status", 140);
        widthMap.put("income", 110);
    }

    public static void resizeToNewWidth(float newWidth) {
        widthMap.put("contractor", 220);
        widthMap.put("typeofcontract", 220);
        widthMap.put("commodities", 140);
        widthMap.put("status", 140);
        widthMap.put("income", 110);

        float rem = newWidth - getWidth();
        if (rem <= 0) return;
        float curr = widthMap.get("commodities");
        int am = Math.floorDiv((int) rem, 100);
        curr += (am * 100);
        widthMap.put("commodities", (int) curr);
    }

    private float currYPos;

    public TradeContractUITable(
            float width, float height, boolean doesHaveScroller, float xCord, float yCord) {
        super(width, height, doesHaveScroller, xCord, yCord);
        if (dropDownButtons.isEmpty()) {
            for (AoTDTradeContract value :
                    AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                dropDownButtons.add(
                        new TradeContractDropDownButton(
                                this, width - 1, 50, 0, 0, value, isContractBrowsingMode));
            }
        }
    }

    public TradeContractUITable(
            float width,
            float height,
            boolean doesHaveScroller,
            float xCord,
            float yCord,
            boolean isUsingContractBrowsingMode) {
        super(width, height, doesHaveScroller, xCord, yCord);
        this.isContractBrowsingMode = isUsingContractBrowsingMode;
        if (dropDownButtons.isEmpty()) {
            for (AoTDTradeContract value :
                    AoTDTradeContractManager.getInstance()
                            .getCurrentlyGeneratedInBrowser()
                            .values()) {
                dropDownButtons.add(
                        new TradeContractDropDownButton(
                                this, width - 1, 50, 0, 0, value, isContractBrowsingMode));
            }
        }
    }

    @Override
    public void recreateTable() {
        clearTable();
        dropDownButtons.clear();
        if (isContractBrowsingMode) {
            for (AoTDTradeContract value :
                    AoTDTradeContractManager.getInstance()
                            .getCurrentlyGeneratedInBrowser()
                            .values()) {
                dropDownButtons.add(
                        new TradeContractDropDownButton(
                                this, width - 1, 50, 0, 0, value, isContractBrowsingMode));
            }
        } else {
            for (AoTDTradeContract value :
                    AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
                dropDownButtons.add(
                        new TradeContractDropDownButton(
                                this, width - 1, 50, 0, 0, value, isContractBrowsingMode));
            }
        }

        // Apply sort later
        createTable();
    }

    public static float getWidth() {
        float x = 0;
        for (Map.Entry<String, Integer> value : widthMap.entrySet()) {
            x += value.getValue() + seperation;
        }
        return x;
    }

    public ButtonAPI buttonCommodity, buttonGraph, buttonSupply, buttonDemand, buttonNet;

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
        buttonCommodity =
                tooltipOfButtons.addAreaCheckbox(
                        "Contractor",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("contractor"),
                        20,
                        0f);
        buttonGraph =
                tooltipOfButtons.addAreaCheckbox(
                        "Contract Type",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("typeofcontract"),
                        20,
                        0f);
        buttonSupply =
                tooltipOfButtons.addAreaCheckbox(
                        "Commodities",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("commodities"),
                        20,
                        0f);
        String textStatus = "Status";
        if (isContractBrowsingMode) {
            textStatus = "Duration";
        }
        buttonDemand =
                tooltipOfButtons.addAreaCheckbox(
                        textStatus,
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("status"),
                        20,
                        0f);
        buttonNet =
                tooltipOfButtons.addAreaCheckbox(
                        "Monthly Income",
                        SortingState.NON_INITIALIZED,
                        base,
                        bg,
                        bright,
                        widthMap.get("income"),
                        20,
                        0f);
        buttonCommodity.getPosition().inTL(seperation, 0);
        buttonGraph.getPosition().rightOfMid(buttonCommodity, seperation);
        buttonSupply.getPosition().rightOfMid(buttonGraph, seperation);
        buttonDemand.getPosition().rightOfMid(buttonSupply, seperation);
        buttonNet.getPosition().rightOfMid(buttonDemand, seperation);
        mainPanel.addUIElement(tooltipOfButtons).inTL(0, 0);
    }

    public String getCurrentlyChosenContract() {
        return currentlyChosenContract;
    }

    @Override
    public void reportButtonPressed(CustomButton buttonPressed) {
        if (buttonPressed instanceof TradeContractCustomButton bt) {
            this.currentlyChosenContract = bt.getContract().getId();
            if (isContractBrowsingMode) {
                AshMisc.placePopUpUI(
                        new ContractDataPopUP(bt.getContract(), this),
                        buttonPressed.getPanel(),
                        400,
                        600);

                //                AshMisc.placePopUpUIInTL(new
                // ContractDataPopUP(bt.getContract()),buttonPressed.getPanel(),400,600,new
                // Vector2f(-bt.getPanel().getPosition().getWidth(),-bt.getPanel().getPosition().getHeight()));
            }
        }
    }
}
