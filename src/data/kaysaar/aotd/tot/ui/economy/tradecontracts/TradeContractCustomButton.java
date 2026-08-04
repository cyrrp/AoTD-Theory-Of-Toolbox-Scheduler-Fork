package data.kaysaar.aotd.tot.ui.economy.tradecontracts;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import java.awt.*;

public class TradeContractCustomButton extends CustomButton {
    public boolean browsingMode = false;

    public TradeContractCustomButton(
            float width,
            float height,
            Object buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright,
            boolean browsingMode) {
        super(width, height, buttonData, indent, base, bg, bright);
        isWithArrow = false;
        this.browsingMode = browsingMode;
    }

    public AoTDTradeContract getContract() {
        return (AoTDTradeContract) buttonData;
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);
        createContainerContent(container);
        tooltip.addCustom(container, 0f).getPosition().inTL(5, 0);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            //            tooltip.addCustom(panelIndicator,0f).getPosition().inTL((float)
            // StarSystemHoldingTable.widthMap.get("name")*0.75f,centerY-7);

        }
    }

    public void createContainerContent(CustomPanelAPI container) {
        AoTDTradeContract contract = getContract();
        if (!browsingMode) {
            ImageViewer viewer =
                    new ImageViewer(
                            container.getPosition().getHeight() - 4,
                            container.getPosition().getHeight() - 4,
                            contract.getIconName());
            container.addComponent(viewer.getComponentPanel()).inTL(2, 2);
            float startingX = viewer.getComponentPanel().getPosition().getWidth() + 5;
            float widthSection = TradeContractUITable.widthMap.get("contractor");
            float opadText = 17f;
            TooltipMakerAPI contractorNameTooltip =
                    container.createUIElement(
                            widthSection - 10 - viewer.getComponentPanel().getPosition().getWidth(),
                            container.getPosition().getHeight(),
                            false);
            LabelAPI label =
                    contractorNameTooltip.addPara(
                            contract.getNameOfContract(),
                            contract.getColorOfContractName(),
                            opadText);
            container.addUIElement(contractorNameTooltip).inTL(startingX, 0);

            startingX = TradeContractUITable.getStartingX("typeofcontract");
            TooltipMakerAPI tlContractType =
                    container.createUIElement(
                            TradeContractUITable.widthMap.get("typeofcontract"),
                            container.getPosition().getHeight(),
                            false);
            if (contract.getSubTypeOfContractString() != null) {
                tlContractType
                        .addPara(contract.getContractType(), contract.getContractTypeColor(), 7f)
                        .setAlignment(Alignment.MID);
                tlContractType
                        .addPara(
                                contract.getSubTypeOfContractString(),
                                contract.getContractTypeColor(),
                                2f)
                        .setAlignment(Alignment.MID);
            } else {
                tlContractType
                        .addPara(
                                contract.getContractType(),
                                contract.getContractTypeColor(),
                                opadText)
                        .setAlignment(Alignment.MID);
            }
            container.addUIElement(tlContractType).inTL(startingX, 0);

            startingX = TradeContractUITable.getStartingX("commodities");
            AoTDCommodityShortPanelCombined commData =
                    new AoTDCommodityShortPanelCombined(
                            TradeContractUITable.widthMap.get("commodities"),
                            contract.getContractData().size(),
                            contract,
                            true,
                            true);
            container
                    .addComponent(commData.getMainPanel())
                    .inTL(
                            startingX,
                            container.getPosition().getHeight() / 2
                                    - (commData.getMainPanel().getPosition().getHeight() / 2));

            startingX = TradeContractUITable.getStartingX("status");
            TooltipMakerAPI tlDuration =
                    container.createUIElement(
                            TradeContractUITable.widthMap.get("status"),
                            container.getPosition().getHeight(),
                            false);
            Pair<String, Color> status = contract.getCurrentContractStatus();
            tlDuration.addPara(status.one, status.two, opadText).setAlignment(Alignment.MID);

            container.addUIElement(tlDuration).inTL(startingX, 0);

            startingX = TradeContractUITable.getStartingX("income");
            TooltipMakerAPI tlIncome =
                    container.createUIElement(
                            TradeContractUITable.widthMap.get("income"),
                            container.getPosition().getHeight(),
                            false);
            Color c = Color.ORANGE;
            int am = contract.getPredictedMoneyWorthForMonth();
            if (am < 0) {
                c = Misc.getNegativeHighlightColor();
            }
            tlIncome.addPara(Misc.getDGSCredits(am), c, opadText).setAlignment(Alignment.MID);
            container.addUIElement(tlIncome).inTL(startingX, 0);
        } else {
            // Assumption of width = 600;
            float efectiveWidth = container.getPosition().getWidth() - 4;
            container
                    .addComponent(
                            DetailedTradeContractUI.createContractorSection(
                                    efectiveWidth, 50, contract, true))
                    .inTL(2, 2);
            TooltipMakerAPI sections = container.createUIElement(efectiveWidth, 80, false);
            sections.addSectionHeading("Additional data", Alignment.MID, 0f);
            sections.addPara(
                    "Duration of contract: %s",
                    3f, Color.ORANGE, contract.getDurationOfContractString());
            sections.addPara(
                    "Payment at the end of month: %s",
                    3f,
                    Color.ORANGE,
                    Misc.getDGSCredits(contract.getPredictedMoneyWorthForMonth()));
            int defaultAmountOfRewards = 1;
            if (!contract.isPrivate()) {
                defaultAmountOfRewards = 2;
            }
            if (contract.getRewards().size() > defaultAmountOfRewards) {
                sections.addPara(
                        "Contractor includes additional rewards",
                        Misc.getPositiveHighlightColor(),
                        3f);
            }

            container.addUIElement(sections).inTL(2, 52);
        }
    }
}
