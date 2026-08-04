package data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.AoTDPlayerContractCreatorManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.rewards.creators.PlayerContractCreatorAPI;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components.CommodityAmountSection;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components.CommodityBigButton;
import data.kaysaar.aotd.tot.ui.economy.tradecontracts.creator.components.CommodityListCustomButton;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ContractCreatorDetailsPlugin implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, componentPanel;
    String currentlyChosen = null;
    AoTDTradeContract currentContract;
    CommodityAmountSection amountSection;
    CustomPanelAPI currentlyPickedSection;

    public AoTDTradeContract getCurrentContract() {
        return currentContract;
    }

    public ContractCreatorDetailsPlugin(float width, float height) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        currentContract = new AoTDTradeContract("test", null, Factions.PLAYER, 9999);
    }

    public ContractCreatorDetailsPlugin(
            float width, float height, AoTDTradeContract existingContract) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        currentContract = existingContract;
        this.id = currentContract.getContractTypeId();
        createUI();
    }

    String id;

    public String getId() {
        return id;
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (componentPanel != null) {
            mainPanel.removeComponent(componentPanel);
        }

        float panelW = mainPanel.getPosition().getWidth();
        float panelH = mainPanel.getPosition().getHeight();
        float height = 60;
        if (AoTDPlayerContractCreatorManager.getCreator(getId()) != null) {
            height = AoTDPlayerContractCreatorManager.getCreator(getId()).getYForExplainSection();
        }
        componentPanel = Global.getSettings().createCustom(panelW, panelH, null);
        TooltipMakerAPI tooltipHeader = componentPanel.createUIElement(panelW, 20, false);
        TooltipMakerAPI tooltipContent = componentPanel.createUIElement(panelW, height, true);
        TooltipMakerAPI tooltipHeader2 = componentPanel.createUIElement(panelW, 20, false);
        final float heightOfBt = 80f;
        final float sepX = 5f;
        final float sepY = 10f;
        final float rightPadding = 15f;
        float usableWidth = panelW - rightPadding;
        // 3 buttons per row
        final float buttonWidth = (usableWidth - 3f * sepX) / 4f;
        float tHeight = 180;
        if (height > 60) {
            tHeight -= height;
            tHeight += 60;
        }
        TooltipMakerAPI listTooltip = componentPanel.createUIElement(panelW, tHeight, true);

        float usableHeight = panelH - 20 - 20 - height - tHeight - 10;

        float currY = 0f;

        if (id != null) {
            PlayerContractCreatorAPI creatorAPI = AoTDPlayerContractCreatorManager.getCreator(id);
            tooltipHeader.setTitleFont(Fonts.ORBITRON_20AA);
            tooltipHeader.addTitle(creatorAPI.getNameOfContract());
            creatorAPI.createContractExplanationSection(tooltipContent, panelW);

            tooltipHeader2
                    .addSectionHeading("Available Commodities To Pick", Alignment.MID, 0f)
                    .getPosition()
                    .inTL(0, 0);
            componentPanel.addUIElement(tooltipHeader).inTL(0f, 0f);
            componentPanel.addUIElement(tooltipContent).inTL(0f, 20f);
            componentPanel.addUIElement(tooltipHeader2).inTL(0f, 20 + height);
            createButtonsSection(
                    creatorAPI,
                    buttonWidth,
                    sepX,
                    panelW,
                    heightOfBt,
                    usableWidth,
                    tHeight,
                    listTooltip,
                    sepY,
                    currY,
                    42 + height,
                    usableHeight);
            handleScrollbarSection(panelW, usableHeight, creatorAPI);
            recreateSectionOfCurrentlyPlacedCommoditiesInOrder(panelW / 2 - 10, usableHeight);
        }

        mainPanel.addComponent(componentPanel).inTL(0f, 0f);
    }

    public void recreateSectionOfCurrentlyPlacedCommoditiesInOrder(float width, float height) {
        if (currentlyPickedSection != null) {
            componentPanel.removeComponent(currentlyPickedSection);
        }
        currentlyPickedSection = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI tlHeader, tlContent, tlTotal, tlTotalLeftSide;
        tlHeader = currentlyPickedSection.createUIElement(width, 20, false);
        tlContent = currentlyPickedSection.createUIElement(width, height - 60, true);
        tlTotal = currentlyPickedSection.createUIElement(width, 20, false);
        tlTotalLeftSide = currentlyPickedSection.createUIElement(width, 20, false);
        tlHeader.addSectionHeading("Current Commodities In Contract", Alignment.MID, 0f);
        for (AoTDTradeContract.TradeContractData value :
                currentContract.getContractData().values()) {
            CommodityListCustomButton bt =
                    new CommodityListCustomButton(
                            width - 15,
                            30,
                            value.getCommodityId(),
                            value.getReqMonthly(),
                            value.getCutFromBasePrice());
            bt.createUI();
            tlContent.addCustom(bt.getMainPanel(), 2f);
        }
        tlTotal.setParaFont(Fonts.ORBITRON_20AA);
        tlTotalLeftSide.setParaFont(Fonts.ORBITRON_20AA);
        tlTotal.addPara(
                        Misc.getDGSCredits(
                                Math.abs(currentContract.getPredictedMoneyWorthForMonth())),
                        Color.ORANGE,
                        0f)
                .setAlignment(Alignment.TR);
        if (currentContract.getPredictedMoneyWorthForMonth() > 0) {
            tlTotalLeftSide.addPara("Total monthly earnings:", 0f);
        } else {
            tlTotalLeftSide.addPara("Total monthly cost:", 0f);
        }
        currentlyPickedSection.addUIElement(tlHeader).inTL(0, 0);
        currentlyPickedSection.addUIElement(tlContent).inTL(-3, 20);
        currentlyPickedSection
                .addUIElement(tlTotal)
                .inTL(0, currentlyPickedSection.getPosition().getHeight() - 25);
        currentlyPickedSection
                .addUIElement(tlTotalLeftSide)
                .inTL(0, currentlyPickedSection.getPosition().getHeight() - 25);

        componentPanel
                .addComponent(currentlyPickedSection)
                .inTL(
                        0,
                        componentPanel.getPosition().getHeight()
                                - currentlyPickedSection.getPosition().getHeight()
                                - 5);
    }

    private void createButtonsSection(
            PlayerContractCreatorAPI creatorAPI,
            float buttonWidth,
            float sepX,
            float panelW,
            float heightOfBt,
            float usableWidth,
            float usableHeight,
            TooltipMakerAPI listTooltip,
            float sepY,
            float currY,
            float yPosToPlace,
            float heightToUseForOtherSection) {
        ArrayList<String> commodities = new ArrayList<>();
        commodities.addAll(creatorAPI.getAvailableCommoditiesForContract());

        for (int i = 0; i < commodities.size(); ) {

            int remaining = commodities.size() - i;
            int inThisRow = Math.min(4, remaining);

            float rowWidth = inThisRow * buttonWidth + (inThisRow - 1) * sepX;

            CustomPanelAPI rowPanel = Global.getSettings().createCustom(panelW, heightOfBt, null);

            float startX;

            if (inThisRow == 4) {
                // Full row starts at 0
                startX = 0f;
            } else {
                // Center inside usable width
                startX = (usableWidth - rowWidth) * 0.5f;
            }

            for (int c = 0; c < inThisRow; c++) {

                final String commodityId = commodities.get(i + c);

                final CommodityBigButton bigBt =
                        new CommodityBigButton(buttonWidth, heightOfBt, commodityId) {
                            @Override
                            public void advance(float amount) {
                                super.advance(amount);
                                if (getCommodityId().equals(currentlyChosen)) {
                                    mainButton.highlight();
                                } else {
                                    mainButton.unhighlight();
                                }
                            }
                        };

                bigBt.setListener(
                        new CustomButton.ButtonEventListener() {
                            @Override
                            public void onButtonClicked() {
                                currentlyChosen = bigBt.getCommodityId();
                                handleScrollbarSection(
                                        panelW, heightToUseForOtherSection, creatorAPI);
                            }
                        });

                bigBt.createUI();
                if (creatorAPI.getMaxLimitForCommodityAmount(commodityId) <= 0) {
                    bigBt.mainButton.setEnabled(false);
                }
                float x = startX + c * (buttonWidth + sepX);
                rowPanel.addComponent(bigBt.getPanel()).inTL(x, 0f);
            }

            listTooltip.addCustom(rowPanel, 0f);
            listTooltip.addSpacer(sepY);

            currY += heightOfBt + sepY;
            i += inThisRow;
        }

        listTooltip.setHeightSoFar(currY);

        // Start from -5 as requested
        componentPanel.addUIElement(listTooltip).inTL(-3f, yPosToPlace);
    }

    private void handleScrollbarSection(
            float panelW, float usableHeight, PlayerContractCreatorAPI creatorAPI) {
        if (currentlyChosen != null) {
            if (amountSection == null) {
                amountSection =
                        new CommodityAmountSection(
                                panelW / 2 - 10,
                                usableHeight,
                                currentlyChosen,
                                currentContract.getMonthlyAmountNeeded(currentlyChosen),
                                creatorAPI.getMaxLimitForCommodityAmount(currentlyChosen),
                                creatorAPI);
                componentPanel
                        .addComponent(amountSection.getMainPanel())
                        .inTL(
                                componentPanel.getPosition().getWidth()
                                        - amountSection.getMainPanel().getPosition().getWidth()
                                        - 5,
                                componentPanel.getPosition().getHeight() - usableHeight - 5);
            } else {
                amountSection.setCreatorAPI(creatorAPI);
                amountSection.setCommodityId(
                        currentlyChosen,
                        currentContract.getMonthlyAmountNeeded(currentlyChosen),
                        creatorAPI.getMaxLimitForCommodityAmount(currentlyChosen));
            }
        } else {
            if (amountSection != null) {
                componentPanel.removeComponent(amountSection.getMainPanel());
                amountSection.clearUI();
                amountSection = null;
            }
        }
    }

    @Override
    public void clearUI() {}

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (amountSection != null && amountSection.isSignalToUpdateAmount() && id != null) {
            amountSection.setSignalToUpdateAmount(false);
            PlayerContractCreatorAPI creatorAPI = AoTDPlayerContractCreatorManager.getCreator(id);
            int am = amountSection.getCurrAmount();
            if (creatorAPI.useUnits()) {
                currentContract.addContractData(
                        currentlyChosen, am, creatorAPI.getCutToPayForCommodity(currentlyChosen));

            } else {
                float per = amountSection.getCurrSegment() / 10f;
                currentContract.addContractData(
                        currentlyChosen, per, creatorAPI.getCutToPayForCommodity(currentlyChosen));
            }
            recreateSectionOfCurrentlyPlacedCommoditiesInOrder(
                    currentlyPickedSection.getPosition().getWidth(),
                    currentlyPickedSection.getPosition().getHeight());
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}

    public void setId(String id) {
        String prev = this.id;
        this.id = id;
        if (!id.equals(prev)) {
            if (amountSection != null) {
                componentPanel.removeComponent(amountSection.getMainPanel());
                amountSection.clearUI();
                currentlyChosen = null;
                amountSection = null;
            }
            currentContract.setContractTypeId(id);
            currentContract.getContractData().clear();
        }
    }
}
