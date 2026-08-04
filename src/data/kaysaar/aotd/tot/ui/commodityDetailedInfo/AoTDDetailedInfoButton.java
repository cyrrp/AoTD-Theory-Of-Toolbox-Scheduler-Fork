package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import java.awt.*;

public class AoTDDetailedInfoButton extends CustomButton {
    boolean showProducers;

    public AoTDDetailedInfoButton(
            float width,
            float height,
            Object buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright,
            boolean showProducers) {
        // Remember that height should be 26
        super(width, height, buttonData, indent, base, bg, bright);
        this.showProducers = showProducers;
        this.setWithArrow(false);
    }

    @Override
    public ButtonAPI createButton(TooltipMakerAPI tooltip) {
        return tooltip.addAreaCheckbox(
                "",
                (Object) null,
                Color.BLACK,
                Color.BLACK,
                Color.BLACK,
                this.panel.getPosition().getWidth(),
                this.panel.getPosition().getHeight(),
                0.0F,
                true);
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);
        createContainerContent(container);
        tooltip.addCustom(container, 0f).getPosition().inTL(0, 0);
        mainButton.setClickable(false);
        mainButton.setMouseOverSound(null);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            //            tooltip.addCustom(panelIndicator,0f).getPosition().inTL((float)
            // StarSystemHoldingTable.widthMap.get("name")*0.75f,centerY-7);

        }
    }

    public void createContainerContent(CustomPanelAPI container) {
        if (buttonData instanceof AoTDCommodityOnMarket commodity) {
            float height = container.getPosition().getHeight() - 2;
            MarketAPI market = commodity.getMarket();
            float textOpad = 5f;
            TooltipMakerAPI tlColony =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("colony"), height, false);
            ImageViewer viewer =
                    new ImageViewer(height, height, commodity.getMarket().getFaction().getCrest());
            tlColony.addCustom(viewer.getComponentPanel(), 0f).getPosition().inTL(4, 0);
            tlColony.addSpacer(0f).getPosition().inTL(height + 10, 0);
            tlColony.addPara(market.getName(), market.getFaction().getBaseUIColor(), textOpad);
            container.addUIElement(tlColony).inTL(0, 1);

            TooltipMakerAPI tlSize =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("size"), height, false);
            tlSize.addPara(market.getSize() + "", market.getFaction().getBaseUIColor(), textOpad)
                    .setAlignment(Alignment.MID);
            container
                    .addUIElement(tlSize)
                    .inTL(AoTDDetailedCommodityUITable.getStartingX("size"), 1);

            TooltipMakerAPI tlFaction =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("faction"), height, false);
            tlFaction
                    .addPara(
                            AoTDToolboxMisc.capitalizeFirst(market.getFaction().getDisplayName()),
                            market.getFaction().getBaseUIColor(),
                            textOpad)
                    .setAlignment(Alignment.MID);
            container
                    .addUIElement(tlFaction)
                    .inTL(AoTDDetailedCommodityUITable.getStartingX("faction"), 1);

            TooltipMakerAPI tlQuantity =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("quantity"), height, false);
            if (showProducers) {
                tlQuantity
                        .addPara(
                                Misc.getWithDGS(
                                        commodity
                                                .getSupplyDemandData()
                                                .getTotalRawUnitsFromSupply()),
                                market.getFaction().getBaseUIColor(),
                                textOpad)
                        .setAlignment(Alignment.MID);
            } else {
                tlQuantity
                        .addPara(
                                Misc.getWithDGS(
                                        commodity
                                                .getSupplyDemandData()
                                                .getTotalRawUnitsFromDemand()),
                                market.getFaction().getBaseUIColor(),
                                textOpad)
                        .setAlignment(Alignment.MID);
            }
            container
                    .addUIElement(tlQuantity)
                    .inTL(AoTDDetailedCommodityUITable.getStartingX("quantity"), 1);

            TooltipMakerAPI tlExcDef =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("excdef"), height, false);
            if (showProducers) {
                String excess = "---";
                Color c = Misc.getGrayColor();
                int exc = commodity.getExcessQuantity();
                if (exc > 0) {
                    excess = Misc.getWithDGS(exc);
                    c = Misc.getPositiveHighlightColor();
                }
                tlExcDef.addPara(excess, c, textOpad).setAlignment(Alignment.MID);
            } else {
                String excess = "---";
                Color c = Misc.getGrayColor();
                int exc = commodity.getDeficitQuantity();
                if (exc > 0) {
                    excess = Misc.getWithDGS(exc);
                    c = Misc.getNegativeHighlightColor();
                }
                tlExcDef.addPara(excess, c, textOpad).setAlignment(Alignment.MID);
            }
            container
                    .addUIElement(tlExcDef)
                    .inTL(AoTDDetailedCommodityUITable.getStartingX("excdef"), 1);
            TooltipMakerAPI acc =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("acc"), height, false);
            float acecss = market.getAccessibilityMod().computeEffective(0f) * 100;
            String per = Misc.getRoundedValueMaxOneAfterDecimal(acecss) + "%";
            acc.addPara(per, textOpad).setAlignment(Alignment.MID);
            container.addUIElement(acc).inTL(AoTDDetailedCommodityUITable.getStartingX("acc"), 1);

            TooltipMakerAPI tlMarketShare =
                    container.createUIElement(
                            AoTDDetailedCommodityUITable.widthMap.get("share"), height, false);
            if (showProducers) {
                tlMarketShare
                        .addPara(
                                String.format(
                                                "%.1f",
                                                AoTDSectorProductionDemandDataUtils
                                                                .getPercentageOfSectorProduction(
                                                                        commodity.getSpec().getId(),
                                                                        commodity
                                                                                .getSupplyDemandData()
                                                                                .getTotalRawUnitsFromSupply())
                                                        * 100f)
                                        + "%",
                                textOpad)
                        .setAlignment(Alignment.MID);
            } else {
                tlMarketShare
                        .addPara("---", Misc.getGrayColor(), textOpad)
                        .setAlignment(Alignment.MID);
            }
            container
                    .addUIElement(tlMarketShare)
                    .inTL(AoTDDetailedCommodityUITable.getStartingX("share"), 1);
        }
    }
}
