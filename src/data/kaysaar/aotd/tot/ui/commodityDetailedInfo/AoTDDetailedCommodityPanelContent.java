package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.ScavengerGuildUtils;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityPanel;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityUITable;
import java.awt.*;
import java.util.List;
import org.lwjgl.input.Keyboard;

public class AoTDDetailedCommodityPanelContent implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;
    MarketAPI market;
    AoTDDetailedCommodityUITable tableProd, tableDem;
    AoTDCommodityPanel commodityPanel;
    public String commodity;
    public String prevCommodity;
    ButtonAPI consumers, producers;
    boolean producerMode = true;
    FactionAPI faction;

    public AoTDDetailedCommodityPanelContent(
            float width, float height, MarketAPI market, String commodityId) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.market = market;
        this.faction = market.getFaction();
        this.commodity = commodityId;
        this.prevCommodity = commodityId;
        createUI();
    }

    public AoTDDetailedCommodityPanelContent(
            float width, float height, FactionAPI market, String commodityId) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.faction = market;
        this.commodity = commodityId;
        this.prevCommodity = commodityId;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            if (commodityPanel != null) {
                contentPanel.removeComponent(commodityPanel.getMainPanel());
            }

            mainPanel.removeComponent(contentPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tooltipHeader =
                contentPanel.createUIElement(AoTDDetailedCommodityUITable.getWidth(), 17, false);
        TooltipMakerAPI tooltipHeader2 =
                contentPanel.createUIElement(AoTDDetailedCommodityUITable.getWidth(), 150, false);
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodity);
        tooltipHeader.addSectionHeading(
                spec.getName() + " : " + AoTDToolboxMisc.capitalizeFirst(faction.getDisplayName()),
                faction.getBaseUIColor(),
                faction.getDarkUIColor(),
                Alignment.MID,
                0f);
        ImageViewer viewer = new ImageViewer(65, 65, spec.getIconName());
        ImageViewer viewer2 = new ImageViewer(65, 65, spec.getIconName());
        tooltipHeader2.addCustom(viewer.getComponentPanel(), 5f).getPosition().inTL(0, 40);
        tooltipHeader2
                .addCustom(viewer2.getComponentPanel(), 5f)
                .getPosition()
                .inTL(AoTDDetailedCommodityUITable.getWidth() - 65, 40);
        TooltipMakerAPI tooltipGP,
                tooltipGD,
                tooltipMV,
                tooltipFactionProd,
                tooltipFactionDem,
                tooltipFactionShare;

        float sectionInBetween = AoTDDetailedCommodityUITable.getWidth() - 140;
        float seperatorFirst = ((sectionInBetween) / 3) - 15;

        tooltipGP = contentPanel.createUIElement(seperatorFirst, 40, false);
        tooltipGD = contentPanel.createUIElement(seperatorFirst, 40, false);
        tooltipMV = contentPanel.createUIElement(seperatorFirst, 40, false);
        tooltipFactionProd = contentPanel.createUIElement(seperatorFirst, 40, false);
        tooltipFactionDem = contentPanel.createUIElement(seperatorFirst, 40, false);
        tooltipFactionShare = contentPanel.createUIElement(seperatorFirst, 40, false);
        int gProd, gDem;
        gProd =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodity)
                        + ScavengerGuildUtils.getCoveredAmountFromSector(commodity);
        gDem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodity);

        int factionProd, factionDem;
        factionDem =
                AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(
                        commodity, faction.getId());
        factionProd =
                AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                        commodity, faction.getId());
        int price =
                AoTDSectorProductionDemandDataUtils.getPriceAmountTotalAroundSector(
                        commodity, gDem, gProd);

        tooltipMV.setParaFont(Fonts.ORBITRON_12);
        setLabel(tooltipMV.addPara("Global Market Value", faction.getBaseUIColor(), 0f));
        tooltipMV.addTooltipToPrevious(
                new AoTDGlobalMarketValueData(commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);
        tooltipMV.setParaFont(Fonts.INSIGNIA_LARGE);
        if (price == 0) {
            setLabel(tooltipMV.addPara("---", Color.ORANGE, 3f));
        } else {
            setLabel(tooltipMV.addPara(Misc.getDGSCredits(price), Color.ORANGE, 3f));
        }
        tooltipMV.addTooltipToPrevious(
                new AoTDGlobalMarketValueData(commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);

        tooltipGP.setParaFont(Fonts.ORBITRON_12);
        LabelAPI label = tooltipGP.addPara("Global Production", faction.getBaseUIColor(), 0f);
        setLabel(label);
        tooltipGP.addTooltipToPrevious(
                new AoTDDetailedComPanelOnHoverImpExp(true, commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);

        tooltipGP.setParaFont(Fonts.INSIGNIA_LARGE);
        setLabel(tooltipGP.addPara(Misc.getWithDGS(gProd), Color.ORANGE, 3f));
        tooltipGP.addTooltipToPrevious(
                new AoTDDetailedComPanelOnHoverImpExp(true, commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);

        tooltipGD.setParaFont(Fonts.ORBITRON_12);
        setLabel(tooltipGD.addPara("Global Demand", faction.getBaseUIColor(), 0f));
        tooltipGD.addTooltipToPrevious(
                new AoTDDetailedComPanelOnHoverImpExp(false, commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);
        tooltipGD.setParaFont(Fonts.INSIGNIA_LARGE);
        setLabel(tooltipGD.addPara(Misc.getWithDGS(gDem), Color.ORANGE, 3f));
        tooltipGD.addTooltipToPrevious(
                new AoTDDetailedComPanelOnHoverImpExp(false, commodity, faction),
                TooltipMakerAPI.TooltipLocation.BELOW,
                false);

        tooltipFactionProd.setParaFont(Fonts.ORBITRON_12);
        setLabel(tooltipFactionProd.addPara("Faction Production", faction.getBaseUIColor(), 0f));

        tooltipFactionProd.setParaFont(Fonts.INSIGNIA_LARGE);
        setLabel(tooltipFactionProd.addPara(Misc.getWithDGS(factionProd), Color.ORANGE, 3f));

        tooltipFactionDem.setParaFont(Fonts.ORBITRON_12);
        setLabel(tooltipFactionDem.addPara("Faction Demand", faction.getBaseUIColor(), 0f));

        tooltipFactionDem.setParaFont(Fonts.INSIGNIA_LARGE);
        setLabel(tooltipFactionDem.addPara(Misc.getWithDGS(factionDem), Color.ORANGE, 3f));

        tooltipFactionShare.setParaFont(Fonts.ORBITRON_12);
        setLabel(tooltipFactionShare.addPara("Faction Share", faction.getBaseUIColor(), 0f));

        tooltipFactionShare.setParaFont(Fonts.INSIGNIA_LARGE);
        setLabel(
                tooltipFactionShare.addPara(
                        String.format(
                                        "%.1f",
                                        AoTDSectorProductionDemandDataUtils
                                                        .getPercentageOfSectorProduction(
                                                                commodity, factionProd)
                                                * 100)
                                + "%",
                        Color.ORANGE,
                        3f));

        contentPanel.addUIElement(tooltipHeader).inTL(0, 0);
        contentPanel.addUIElement(tooltipHeader2).inTL(0, 20);

        contentPanel.addUIElement(tooltipMV).inTL(80, 25);
        contentPanel.addUIElement(tooltipGP).inTL(80 + seperatorFirst + 5, 25);
        contentPanel.addUIElement(tooltipGD).inTL(80 + (seperatorFirst * 2) + 10, 25);

        contentPanel.addUIElement(tooltipFactionShare).inTL(80, 80);
        contentPanel.addUIElement(tooltipFactionProd).inTL(80 + seperatorFirst + 5, 80);
        contentPanel.addUIElement(tooltipFactionDem).inTL(80 + (seperatorFirst * 2) + 10, 80);
        TooltipMakerAPI tooltipButtons =
                contentPanel.createUIElement(AoTDDetailedCommodityUITable.getWidth(), 20, false);
        producers =
                tooltipButtons.addButton(
                        "Producers",
                        null,
                        faction.getBaseUIColor(),
                        faction.getDarkUIColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        150,
                        20,
                        0f);
        producers.getPosition().inTL(0, 0);

        consumers =
                tooltipButtons.addButton(
                        "Consumers",
                        null,
                        faction.getBaseUIColor(),
                        faction.getDarkUIColor(),
                        Alignment.MID,
                        CutStyle.TOP,
                        150,
                        20,
                        0f);
        consumers.getPosition().inTL(155, 0);
        producers.setShortcut(Keyboard.KEY_1, false);
        consumers.setShortcut(Keyboard.KEY_2, false);
        contentPanel.addUIElement(tooltipButtons).inTL(0, 130);
        if (commodityPanel == null && market != null) {
            commodityPanel =
                    new AoTDCommodityPanel(
                            AoTDCommodityUITable.getWidth(),
                            contentPanel.getPosition().getHeight(),
                            market,
                            true,
                            this);
        }
        if (tableDem == null) {
            tableDem =
                    new AoTDDetailedCommodityUITable(
                            AoTDDetailedCommodityUITable.getWidth(),
                            contentPanel.getPosition().getHeight() - 180,
                            true,
                            0,
                            0,
                            false,
                            faction,
                            commodity);
            tableDem.createSections();
            tableDem.createTable();
        }
        if (tableProd == null) {
            tableProd =
                    new AoTDDetailedCommodityUITable(
                            AoTDDetailedCommodityUITable.getWidth(),
                            contentPanel.getPosition().getHeight() - 180,
                            true,
                            0,
                            0,
                            true,
                            faction,
                            commodity);
            tableProd.createSections();
            tableProd.createTable();
        }
        if (!prevCommodity.equals(commodity)) {
            prevCommodity = commodity;
            tableDem.setCommodityId(commodity);
            tableProd.setCommodityId(commodity);
        }

        if (producerMode) {
            contentPanel.addComponent(tableProd.mainPanel).inTL(0, 155);
        } else {
            contentPanel.addComponent(tableDem.mainPanel).inTL(0, 155);
        }
        if (market != null) {
            contentPanel
                    .addComponent(commodityPanel.getMainPanel())
                    .inTL(AoTDDetailedCommodityUITable.getWidth() + 18, 0);
        }

        mainPanel.addComponent(contentPanel);
    }

    private void setLabel(LabelAPI label) {
        label.setAlignment(Alignment.MID);
        label.setHighlightOnMouseover(true);
    }

    @Override
    public void clearUI() {
        if (commodityPanel != null) {
            this.commodityPanel.clearUI();
        }

        this.tableDem.clearUI();
        this.tableProd.clearUI();
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (consumers.isChecked()) {
            consumers.setChecked(false);
            if (producerMode) {
                producerMode = false;
                createUI();
            }
        }
        if (producers.isChecked()) {
            producers.setChecked(false);
            if (!producerMode) {
                producerMode = true;
                createUI();
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
