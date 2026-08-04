package data.kaysaar.aotd.tot.ui.economy.tradecontracts.browser;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.economy.EconomyTradeDealsData;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.DetailedTradeContractUI;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.TradeContractCustomButton;
import java.util.ArrayList;
import java.util.List;

public class ContractBrowsingPanelPlugin implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, panelForOffers;
    public static boolean updateUIStuff = false;
    String currContract = null;
    DetailedTradeContractUI contractUI;

    public String getCurrContract() {
        return currContract;
    }

    public ContractBrowsingPanelPlugin(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    ArrayList<TradeContractCustomButton> bts = new ArrayList<>();

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (panelForOffers != null) {
            mainPanel.removeComponent(panelForOffers);
        }
        float buttonWidth = 330;
        panelForOffers =
                Global.getSettings()
                        .createCustom(
                                buttonWidth * 2 + 20, mainPanel.getPosition().getHeight(), null);
        if (contractUI == null) {
            contractUI =
                    new DetailedTradeContractUI(
                            mainPanel.getPosition().getWidth()
                                    - panelForOffers.getPosition().getWidth()
                                    - 10,
                            mainPanel.getPosition().getHeight() - 30,
                            null,
                            true);
        }

        float buttonHeight = 135f;

        float sepX = 5f;
        float sepY = 5f;

        float containerW = buttonWidth * 2f + sepX; // 2 buttons + spacing
        float currY = 0f;
        TooltipMakerAPI contentTooltip =
                panelForOffers.createUIElement(
                        buttonWidth * 2 + 20, panelForOffers.getPosition().getHeight() - 20, true);
        contentTooltip.addSpacer(0f).getPosition().inTL(0, 0);
        List<AoTDTradeContract> contracts =
                new java.util.ArrayList<>(
                        AoTDTradeContractManager.getInstance()
                                .getCurrentlyGeneratedInBrowser()
                                .values());
        for (int i = 0; i < contracts.size(); ) {
            // How many items in this row (2 or 1)
            int remaining = contracts.size() - i;
            int inThisRow = Math.min(2, remaining);

            // Row panel (exact positioning inside)
            CustomPanelAPI rowPanel =
                    Global.getSettings().createCustom(containerW, buttonHeight, null);

            if (inThisRow == 2) {
                // Left
                AoTDTradeContract c0 = contracts.get(i);
                TradeContractCustomButton bt0 =
                        new TradeContractCustomButton(
                                buttonWidth,
                                buttonHeight,
                                c0,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                true);
                bt0.createUI();
                bts.add(bt0);
                rowPanel.addComponent(bt0.getPanel()).inTL(0f, 0f);

                // Right
                AoTDTradeContract c1 = contracts.get(i + 1);
                TradeContractCustomButton bt1 =
                        new TradeContractCustomButton(
                                buttonWidth,
                                buttonHeight,
                                c1,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                true);
                bt1.createUI();
                bts.add(bt1);
                rowPanel.addComponent(bt1.getPanel()).inTL(buttonWidth + sepX, 0f);

            } else {
                // Single -> centered
                AoTDTradeContract c0 = contracts.get(i);
                TradeContractCustomButton bt0 =
                        new TradeContractCustomButton(
                                buttonWidth,
                                buttonHeight,
                                c0,
                                0f,
                                Misc.getBasePlayerColor(),
                                Misc.getDarkPlayerColor(),
                                Misc.getBrightPlayerColor(),
                                true);
                bt0.createUI();
                bts.add(bt0);

                float x = (containerW - buttonWidth) * 0.5f;
                rowPanel.addComponent(bt0.getPanel()).inTL(x, 0f);
            }

            // Add row to tooltip
            contentTooltip.addCustom(rowPanel, 0f);
            contentTooltip.addSpacer(sepY);

            currY += buttonHeight + sepY;
            i += inThisRow;
        }

        contentTooltip.setHeightSoFar(currY);
        float additional = 0f;
        if (currY <= panelForOffers.getPosition().getHeight() - 20) {
            additional -= 10;
        }
        TooltipMakerAPI headerTooltip =
                panelForOffers.createUIElement(buttonWidth * 2 + 15 + additional, 20, false);
        headerTooltip.addSectionHeading("Contract Posted", Alignment.MID, 0f);
        panelForOffers.addUIElement(headerTooltip).inTL(0, 0);
        panelForOffers.addUIElement(contentTooltip).inTL(-5, 20);
        mainPanel.addComponent(panelForOffers).inTL(0, 0);
        mainPanel
                .addComponent(contractUI.getMainPanel())
                .inTL(
                        mainPanel.getPosition().getWidth()
                                - contractUI.getMainPanel().getPosition().getWidth()
                                - 5,
                        0);
    }

    @Override
    public void clearUI() {

        if (updateUIStuff) {
            EconomyTradeDealsData.forceTableUpdate = true;
            updateUIStuff = false;
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        for (TradeContractCustomButton bt : bts) {
            if (bt.isChecked() && bt.mainButton.isEnabled()) {
                bt.setChecked(false);
                if (currContract == null || !bt.getContract().getId().equals(currContract)) {
                    currContract = bt.getContract().getId();
                    contractUI.setContract(bt.getContract());
                }
            }
        }
        if (contractUI != null && contractUI.accept != null && contractUI.accept.isChecked()) {
            contractUI.accept.setChecked(false);
            for (TradeContractCustomButton bt : bts) {
                if (bt.getContract().getId().equals(currContract)) {
                    bt.setChecked(true);
                    bt.mainButton.setEnabled(false);
                }
            }
            contractUI.setContract(null);
            AoTDTradeContract contract =
                    AoTDTradeContractManager.getInstance()
                            .getCurrentlyGeneratedInBrowser()
                            .remove(currContract);
            contract.setWasTaken(true);
            AoTDTradeContractManager.getInstance().addContract(contract);
            updateUIStuff = true;
            currContract = null;
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
