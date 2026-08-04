package data.kaysaar.aotd.tot.ui.economy.commoditydata.table;

import ashlib.data.plugins.ui.models.CustomButton;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.ui.components.CommodityGraphComponent;
import java.awt.*;

public class AoTDCommodityProductionButton extends CustomButton {
    public static class AoTDCommodityProductionButtonData {
        String commodityId;
        String factionId;
        int months;
        boolean showAll;

        public AoTDCommodityProductionButtonData(String commodityId, String factionId, int months) {
            this.commodityId = commodityId;
            this.factionId = factionId;
            this.months = months;
            showAll = false;
        }

        public AoTDCommodityProductionButtonData(
                String commodityId, String factionId, boolean showAll) {
            this.commodityId = commodityId;
            this.factionId = factionId;
            showAll = true;
        }

        public String getCommodityId() {
            return commodityId;
        }

        public String getFactionId() {
            return factionId;
        }
    }

    public AoTDCommodityProductionButton(
            float width,
            float height,
            AoTDCommodityProductionButtonData buttonData,
            float indent,
            Color base,
            Color bg,
            Color bright) {
        super(width, height, buttonData, indent, base, bg, bright);
    }

    public AoTDCommodityProductionButtonData getData() {
        return (AoTDCommodityProductionButtonData) buttonData;
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
        AoTDCommodityProductionButtonData data = getData();
        float startingX = AoTDCommodityProductionDataTable.getStartingX("commodity") + 5;
        float widthOfCom = AoTDCommodityProductionDataTable.widthMap.get("commodity");
        float iconSize = 40;
        float opadText = 16f;
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(data.commodityId);
        ImageViewer viewer = new ImageViewer(iconSize, iconSize, spec.getIconName());
        TooltipMakerAPI tl =
                container.createUIElement(
                        widthOfCom - iconSize - 10, container.getPosition().getHeight(), false);
        LabelAPI la = tl.addPara(spec.getName(), opadText);
        if (la.computeTextWidth(la.getText()) > widthOfCom - iconSize - 15) {
            la.getPosition().inTL(5, 9);
        }
        container.addComponent(viewer.getComponentPanel()).inTL(startingX, 5);
        container.addUIElement(tl).inTL(startingX + iconSize + 5, 0);
        CommodityGraphComponent component = null;
        if (data.showAll) {
            component =
                    new CommodityGraphComponent(
                            AoTDCommodityProductionDataTable.widthMap.get("graph"),
                            40,
                            data.commodityId,
                            data.factionId,
                            Integer.MAX_VALUE);

        } else {
            component =
                    new CommodityGraphComponent(
                            AoTDCommodityProductionDataTable.widthMap.get("graph"),
                            40,
                            data.commodityId,
                            data.factionId,
                            data.months);
        }
        container
                .addComponent(component.getMainPanel())
                .inTL(AoTDCommodityProductionDataTable.getStartingX("graph"), 5);

        TooltipMakerAPI tlSupply, tlDemand, tlNet;
        tlSupply =
                container.createUIElement(
                        AoTDCommodityProductionDataTable.widthMap.get("supply"),
                        container.getPosition().getHeight(),
                        false);
        tlDemand =
                container.createUIElement(
                        AoTDCommodityProductionDataTable.widthMap.get("demand"),
                        container.getPosition().getHeight(),
                        false);
        tlNet =
                container.createUIElement(
                        AoTDCommodityProductionDataTable.widthMap.get("net"),
                        container.getPosition().getHeight(),
                        false);

        int supply =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                        data.commodityId, data.factionId);
        int demand =
                AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(
                        data.commodityId, data.factionId);
        int net = supply - demand;
        Color supplyC, demandC, netC;
        if (net < 0) {
            supplyC = Color.ORANGE;
            demandC = Misc.getNegativeHighlightColor();
            netC = Misc.getNegativeHighlightColor();
        } else if (net > 0) {
            supplyC = Misc.getPositiveHighlightColor();
            demandC = Color.ORANGE;
            netC = Misc.getPositiveHighlightColor();
        } else {
            supplyC = Color.ORANGE;
            demandC = Color.ORANGE;
            netC = Color.ORANGE;
        }
        tlSupply.addPara(Misc.getWithDGS(supply), supplyC, opadText).setAlignment(Alignment.MID);
        tlDemand.addPara(Misc.getWithDGS(demand), demandC, opadText).setAlignment(Alignment.MID);
        tlNet.addPara(Misc.getWithDGS(net), netC, opadText).setAlignment(Alignment.MID);
        container
                .addUIElement(tlSupply)
                .inTL(AoTDCommodityProductionDataTable.getStartingX("supply"), 0);
        container
                .addUIElement(tlDemand)
                .inTL(AoTDCommodityProductionDataTable.getStartingX("demand"), 0);
        container.addUIElement(tlNet).inTL(AoTDCommodityProductionDataTable.getStartingX("net"), 0);
    }
}
