package data.kaysaar.aotd.tot.ui.commoditypanel;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import ashlib.data.plugins.ui.plugins.UILinesRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import java.awt.*;

public class AoTDCommodityInfoButton extends CustomButton {
    public AoTDCommodityInfoButton(
            float width,
            float height,
            Object buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright) {
        super(width, height, buttonData, indent, base, bg, bright);
        this.setWithArrow(false);
    }

    @Override
    public void createButtonContent(TooltipMakerAPI tooltip) {
        CustomPanelAPI container = Global.getSettings().createCustom(this.width, this.height, null);
        createContainerContent(container);
        tooltip.addCustom(container, 0f).getPosition().inTL(0, 0);
        float centerY = height / 2;
        if (isWithArrow) {
            panelIndicator = Global.getSettings().createCustom(15, 15, null);
            //            tooltip.addCustom(panelIndicator,0f).getPosition().inTL((float)
            // StarSystemHoldingTable.widthMap.get("name")*0.75f,centerY-7);

        }
    }

    public void createContainerContent(CustomPanelAPI container) {
        if (buttonData instanceof AoTDCommodityOnMarket commodity) {
            float iconSize = 31;
            ImageViewer viewer =
                    new ImageViewer(iconSize, iconSize, commodity.getSpec().getIconName()) {
                        @Override
                        public void render(float alphaMult) {
                            this.spriteOfImage.setAlphaMult(alphaMult * this.alphaMult);
                            this.spriteOfImage.setSize(
                                    this.componentPanel.getPosition().getWidth() * 0.9f,
                                    this.componentPanel.getPosition().getHeight() * 0.9f);
                            this.spriteOfImage.renderAtCenter(
                                    this.componentPanel.getPosition().getCenterX(),
                                    this.componentPanel.getPosition().getCenterY());
                        }
                    };

            float startingX = AoTDCommodityUITable.getStartingX("commodity") + iconSize + 5 + 20;
            container.addComponent(viewer.getComponentPanel()).inTL(startingX, 5);
            if (!commodity.isSupplyLegal() || !commodity.isDemandLegal()) {
                UILinesRenderer renderer = new UILinesRenderer(0f);
                renderer.setPanel(viewer.getComponentPanel());
                renderer.setBoxColor(Misc.getNegativeHighlightColor());
                CustomPanelAPI holder = Global.getSettings().createCustom(1, 1, renderer);
                container.addComponent(holder).inTL(0, 0);
            }
            TooltipMakerAPI production, demand, imports, exports;
            int prod, dem;
            prod = commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
            dem = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
            int rawExp = prod - dem;
            int deficit = commodity.getDeficitQuantity();
            int excess = commodity.getExcessQuantity();
            production =
                    container.createUIElement(
                            AoTDCommodityUITable.widthMap.get("production"),
                            container.getPosition().getHeight(),
                            false);
            demand =
                    container.createUIElement(
                            AoTDCommodityUITable.widthMap.get("demand"),
                            container.getPosition().getHeight(),
                            false);
            imports =
                    container.createUIElement(
                            AoTDCommodityUITable.widthMap.get("import"),
                            container.getPosition().getHeight(),
                            false);
            exports =
                    container.createUIElement(
                            AoTDCommodityUITable.widthMap.get("deficit"),
                            container.getPosition().getHeight(),
                            false);
            Color prodC, demC, expC, excDef;
            if (prod > 0) {
                prodC = Misc.getPositiveHighlightColor();
            } else {
                prodC = Misc.getGrayColor();
            }
            if (dem > 0) {
                demC = Misc.getNegativeHighlightColor();
            } else {
                demC = Misc.getGrayColor();
            }

            production
                    .addPara(AoTDToolboxMisc.getDGSStringWithSign(prod), prodC, 0f)
                    .setAlignment(Alignment.MID);
            demand.addPara(Misc.getWithDGS(-dem), demC, 0f).setAlignment(Alignment.MID);
            if (rawExp > 0) {
                expC = Misc.getPositiveHighlightColor();
                imports.addPara(Misc.getWithDGS(rawExp), expC, 0f).setAlignment(Alignment.MID);
                ImageViewer viewer1 =
                        new ImageViewer(
                                iconSize - 10,
                                iconSize - 10,
                                Global.getSettings().getSpriteName("commodity_markers", "exports"));
                container
                        .addComponent(viewer1.getComponentPanel())
                        .leftOfMid(viewer.getComponentPanel(), 13);
            } else {
                expC = Color.ORANGE;
                if (rawExp == 0) expC = Misc.getGrayColor();
                if (rawExp < 0) {
                    rawExp *= -1;
                }
                imports.addPara(Misc.getWithDGS(rawExp), expC, 0f).setAlignment(Alignment.MID);
                ImageViewer viewer1 =
                        new ImageViewer(
                                iconSize - 10,
                                iconSize - 10,
                                Global.getSettings().getSpriteName("commodity_markers", "imports"));
                container
                        .addComponent(viewer1.getComponentPanel())
                        .leftOfMid(viewer.getComponentPanel(), 13);
            }
            if (excess > 0) {
                excDef = Misc.getPositiveHighlightColor();
                exports.addPara(AoTDToolboxMisc.getDGSStringWithSign(excess), excDef, 0f)
                        .setAlignment(Alignment.MID);
            }
            if (deficit > 0) {
                excDef = Misc.getNegativeHighlightColor();
                exports.addPara(AoTDToolboxMisc.getDGSStringWithSign(-deficit), excDef, 0f)
                        .setAlignment(Alignment.MID);
            }
            if (deficit == 0 && excess == 0) {
                excDef = Misc.getGrayColor();
                exports.addPara("0", excDef, 0f).setAlignment(Alignment.MID);
            }
            container
                    .addUIElement(production)
                    .inTL(
                            AoTDCommodityUITable.getStartingX("production") + 5,
                            container.getPosition().getHeight() / 2 - 10);
            container
                    .addUIElement(demand)
                    .inTL(
                            AoTDCommodityUITable.getStartingX("demand") + 5,
                            container.getPosition().getHeight() / 2 - 10);
            container
                    .addUIElement(imports)
                    .inTL(
                            AoTDCommodityUITable.getStartingX("import") + 5,
                            container.getPosition().getHeight() / 2 - 10);
            container
                    .addUIElement(exports)
                    .inTL(
                            AoTDCommodityUITable.getStartingX("deficit") + 5,
                            container.getPosition().getHeight() / 2 - 10);
        }
    }

    public AoTDCommodityOnMarket getData() {
        return (AoTDCommodityOnMarket) buttonData;
    }
}
