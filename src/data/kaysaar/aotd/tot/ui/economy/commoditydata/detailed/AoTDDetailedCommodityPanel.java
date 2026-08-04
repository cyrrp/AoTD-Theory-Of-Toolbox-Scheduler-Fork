package data.kaysaar.aotd.tot.ui.economy.commoditydata.detailed;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.AoTDDetailedCommodityUITable;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.CommodityDetailDialog;
import java.awt.*;
import java.util.List;

public class AoTDDetailedCommodityPanel implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;
    String commodityId, factionId;
    ButtonAPI checkSectorCurrentEconomyData;

    public AoTDDetailedCommodityPanel(
            float width, float height, String commodityId, String factionId) {
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.commodityId = commodityId;
        this.factionId = factionId;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public void setCurrCommodityId(String currCommodityId) {
        String prev = this.commodityId;
        this.commodityId = currCommodityId;
        if (!this.commodityId.equals(prev)) {
            createUI();
        }
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }
        FactionAPI colorFaction = Global.getSector().getPlayerFaction();
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tl =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 75, false);
        TooltipMakerAPI tlContent =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight() - 75,
                        false);
        tl.setParaFont(Fonts.ORBITRON_20AA);
        tl.addPara(spec.getName(), 0).setAlignment(Alignment.MID);
        ImageViewer viewer = new ImageViewer(50, 50, spec.getIconName());
        tl.addCustom(viewer.getComponentPanel(), 5f)
                .getPosition()
                .inTL(
                        contentPanel.getPosition().getWidth() / 2
                                - (viewer.getComponentPanel().getPosition().getWidth() / 2),
                        25);
        tlContent.addPara(
                Global.getSettings()
                        .getDescription(spec.getId(), Description.Type.RESOURCE)
                        .getText1(),
                3f);
        tlContent.addSectionHeading(
                "Estimated Earnings from Trade",
                colorFaction.getBaseUIColor(),
                colorFaction.getDarkUIColor(),
                Alignment.MID,
                5f);

        int am = 0;
        if (factionId.equals(Factions.NEUTRAL)) {
            for (FactionAPI factionAPI : AoTDToolboxMisc.getFactionsInEconomy()) {
                for (MarketAPI factionMarket : Misc.getFactionMarkets(factionAPI)) {
                    am +=
                            AoTDToolboxMisc.getExpectedMonthlyIncomeFromCommodity(
                                    AoTDCommodityOnMarket.getComMarketInstanceSave(
                                            factionMarket, commodityId));
                }
            }
        } else {
            for (MarketAPI factionMarket : Misc.getFactionMarkets(factionId)) {
                am +=
                        AoTDToolboxMisc.getExpectedMonthlyIncomeFromCommodity(
                                AoTDCommodityOnMarket.getComMarketInstanceSave(
                                        factionMarket, commodityId));
            }
        }
        if (am > 0) {
            if (factionId.equals(Factions.NEUTRAL)) {
                tlContent.addPara(
                        "Trade value of entire sector is estimated to be around %s.",
                        3f, Color.ORANGE, Misc.getDGSCredits(am), spec.getName());

            } else {
                tlContent.addPara(
                        AoTDToolboxMisc.capitalizeFirst(
                                        Global.getSector().getFaction(factionId).getDisplayName())
                                + " earns in total %s from selling %s.",
                        3f,
                        Color.ORANGE,
                        Misc.getDGSCredits(am),
                        spec.getName());
            }
        } else {
            tlContent.addPara("No earnings are reported from said commodity.", 3f);
        }
        if (!factionId.equals(Factions.NEUTRAL)) {
            checkSectorCurrentEconomyData =
                    tlContent.addButton(
                            "Check sector-wide current economy data",
                            null,
                            Misc.getBasePlayerColor(),
                            Misc.getDarkPlayerColor(),
                            Alignment.MID,
                            CutStyle.TL_BR,
                            contentPanel.getPosition().getWidth() - 10f,
                            25,
                            10);
            checkSectorCurrentEconomyData.setEnabled(
                    Global.getSector().getIntelManager().isPlayerInRangeOfCommRelay());
            tlContent.addTooltipToPrevious(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 450f;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            tooltip.addPara(
                                    "Provides access to up-to-date sector-wide data on supply and demand for this commodity.",
                                    3f);

                            tooltip.addPara(
                                    "Requires to be within effective range of a %s to access this information.",
                                    3f, Color.ORANGE, "Comm Relay");
                        }
                    },
                    TooltipMakerAPI.TooltipLocation.LEFT,
                    false);
        }

        tlContent.addSpacer(5f);
        float heightSoFar = tlContent.getHeightSoFar();
        float remHeight = contentPanel.getPosition().getHeight() - heightSoFar - 200;
        float heightEach = (remHeight / 2);

        tlContent.addCustom(
                createTablePanel(contentPanel.getPosition().getWidth(), heightEach, true), 5f);
        tlContent.addCustom(
                createTablePanel(contentPanel.getPosition().getWidth(), heightEach, false), 5f);
        tlContent.addCustom(
                createTablePanelContracts(contentPanel.getPosition().getWidth(), 100), 5f);

        contentPanel.addUIElement(tl).inTL(0, 0);
        contentPanel.addUIElement(tlContent).inTL(0, 75);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    public CustomPanelAPI createTablePanelContracts(float width, float height) {
        CustomPanelAPI mainPanel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI headerTooltip =
                mainPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        FactionAPI colorFaction = Global.getSector().getPlayerFaction();
        headerTooltip.addSectionHeading(
                "Trade Contracts",
                colorFaction.getBaseUIColor(),
                colorFaction.getDarkUIColor(),
                Alignment.MID,
                0f);
        mainPanel.addUIElement(headerTooltip).inTL(-5, 0);
        TooltipMakerAPI contentTooltip =
                mainPanel.createUIElement(mainPanel.getPosition().getWidth(), height - 25, true);
        float initOpad = 1f;
        for (AoTDTradeContract value :
                AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (value.getContractData().get(commodityId) != null) {
                contentTooltip.addCustom(createRow(width, 20, value), initOpad);
                initOpad = 3f;
            }
        }

        mainPanel.addUIElement(contentTooltip).inTL(-5, 25);
        return mainPanel;
    }

    public CustomPanelAPI createTablePanel(float width, float height, boolean isProducers) {
        CustomPanelAPI mainPanel = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI headerTooltip =
                mainPanel.createUIElement(mainPanel.getPosition().getWidth(), 20, false);
        FactionAPI colorFaction = Global.getSector().getPlayerFaction();
        if (Factions.NEUTRAL.equals(factionId)) {
            if (isProducers) {
                headerTooltip.addSectionHeading(
                        "Sector Producers",
                        colorFaction.getBaseUIColor(),
                        colorFaction.getDarkUIColor(),
                        Alignment.MID,
                        0f);
            } else {
                headerTooltip.addSectionHeading(
                        "Sector Consumers",
                        colorFaction.getBaseUIColor(),
                        colorFaction.getDarkUIColor(),
                        Alignment.MID,
                        0f);
            }
            mainPanel.addUIElement(headerTooltip).inTL(-5, 0);
            TooltipMakerAPI contentTooltip =
                    mainPanel.createUIElement(
                            mainPanel.getPosition().getWidth(), height - 25, true);
            float initOpad = 1f;
            if (isProducers) {
                for (MarketAPI marketAPI :
                        AoTDSectorProductionDemandDataUtils.getFactionMarketsProducers(
                                commodityId, factionId)) {
                    contentTooltip.addCustom(createRow(width, 20, marketAPI, true), initOpad);
                    initOpad = 3f;
                }
            } else {
                for (MarketAPI marketAPI :
                        AoTDSectorProductionDemandDataUtils.getFactionMarketsConsumers(
                                commodityId, factionId)) {
                    contentTooltip.addCustom(createRow(width, 20, marketAPI, false), initOpad);
                    initOpad = 3f;
                }
            }
            mainPanel.addUIElement(contentTooltip).inTL(-5, 25);
        } else {
            if (isProducers) {
                headerTooltip.addSectionHeading(
                        "Faction Producers",
                        colorFaction.getBaseUIColor(),
                        colorFaction.getDarkUIColor(),
                        Alignment.MID,
                        0f);
            } else {
                headerTooltip.addSectionHeading(
                        "Faction Consumers",
                        colorFaction.getBaseUIColor(),
                        colorFaction.getDarkUIColor(),
                        Alignment.MID,
                        0f);
            }
            mainPanel.addUIElement(headerTooltip).inTL(-5, 0);
            TooltipMakerAPI contentTooltip =
                    mainPanel.createUIElement(
                            mainPanel.getPosition().getWidth(), height - 25, true);
            float initOpad = 1f;
            if (isProducers) {
                for (MarketAPI marketAPI :
                        AoTDSectorProductionDemandDataUtils.getFactionMarketsProducers(
                                commodityId, factionId)) {
                    contentTooltip.addCustom(createRow(width, 20, marketAPI, true), initOpad);
                    initOpad = 3f;
                }
            } else {
                for (MarketAPI marketAPI :
                        AoTDSectorProductionDemandDataUtils.getFactionMarketsConsumers(
                                commodityId, factionId)) {
                    contentTooltip.addCustom(createRow(width, 20, marketAPI, false), initOpad);
                    initOpad = 3f;
                }
            }
            mainPanel.addUIElement(contentTooltip).inTL(-5, 25);
        }

        return mainPanel;
    }

    public CustomPanelAPI createColonySection(float width, float height, MarketAPI market) {
        CustomPanelAPI section = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI headerTooltip = section.createUIElement(width - height - 10, height, false);
        ImageViewer viewer =
                new ImageViewer(
                        height,
                        height,
                        Global.getSector().getFaction(market.getFactionId()).getCrest());
        section.addComponent(viewer.getComponentPanel()).inTL(0, 0);
        LabelAPI label =
                headerTooltip.addPara(
                        market.getName() + " ( size " + market.getSize() + " )",
                        market.getFaction().getBaseUIColor(),
                        0f);
        label.getPosition().inTL(0, height / 2 - (label.computeTextHeight(label.getText()) / 2));
        section.addUIElement(headerTooltip).inTL(height + 5, 0);
        return section;
    }

    public CustomPanelAPI createContractSection(
            float width, float height, AoTDTradeContract market) {
        CustomPanelAPI section = Global.getSettings().createCustom(width, height, null);
        TooltipMakerAPI headerTooltip = section.createUIElement(width - height - 10, height, false);
        TooltipMakerAPI test = section.createUIElement(10000, height, false);
        ImageViewer viewer = new ImageViewer(height, height, market.getIconName());
        section.addComponent(viewer.getComponentPanel()).inTL(0, 0);
        LabelAPI labelT =
                test.addPara(
                        market.getNameOfContract() + " " + market.getContractType(),
                        market.getColorOfContractName(),
                        0f);
        LabelAPI label;
        if (market.isIssuedByPlayer()) {
            label =
                    headerTooltip.addPara(
                            market.getContractType() + " " + market.getSubTypeOfContractString(),
                            market.getColorOfContractName(),
                            0f);
        } else {
            label =
                    headerTooltip.addPara(
                            market.getNameOfContract() + " " + market.getContractType(),
                            market.getColorOfContractName(),
                            0f);
        }
        label.getPosition().inTL(0, height / 2 - (label.computeTextHeight(label.getText()) / 2));
        section.addUIElement(headerTooltip).inTL(height + 5, 0);
        return section;
    }

    public CustomPanelAPI createRow(
            float width, float height, MarketAPI market, boolean production) {
        CustomPanelAPI section = Global.getSettings().createCustom(width, height, null);
        CustomPanelAPI colonySection = createColonySection(width * 0.7f - 5f, height, market);
        TooltipMakerAPI headerTooltip = section.createUIElement(width * 0.3f, height, false);
        float labelWidth = width * 0.3f;
        LabelAPI label = null;
        AoTDCommodityOnMarket marketCom =
                AoTDCommodityOnMarket.getComMarketInstanceSave(market, commodityId);
        if (production) {
            label =
                    headerTooltip.addPara(
                            Misc.getWithDGS(
                                    marketCom.getSupplyDemandData().getTotalRawUnitsFromSupply()),
                            Misc.getPositiveHighlightColor(),
                            0f);
        } else {
            label =
                    headerTooltip.addPara(
                            Misc.getWithDGS(
                                    marketCom.getSupplyDemandData().getTotalRawUnitsFromDemand()),
                            Color.ORANGE,
                            0f);
        }
        label.getPosition()
                .inTL(
                        labelWidth / 2 - (label.computeTextWidth(label.getText())),
                        height / 2 - (label.computeTextHeight(label.getText()) / 2));
        section.addComponent(colonySection).inTL(0, 0);
        section.addUIElement(headerTooltip).inTL(width * 0.7f + 5, 0);
        return section;
    }

    public CustomPanelAPI createRow(float width, float height, AoTDTradeContract contract) {
        CustomPanelAPI section = Global.getSettings().createCustom(width, height, null);
        CustomPanelAPI colonySection = createContractSection(width * 0.7f - 5f, height, contract);
        TooltipMakerAPI headerTooltip = section.createUIElement(width * 0.3f, height, false);
        float labelWidth = width * 0.3f;
        LabelAPI label = null;
        label =
                headerTooltip.addPara(
                        Misc.getWithDGS(
                                contract.getContractData().get(commodityId).getReqMonthly()),
                        Color.ORANGE,
                        0f);
        label.getPosition()
                .inTL(
                        labelWidth / 2 - (label.computeTextWidth(label.getText())),
                        height / 2 - (label.computeTextHeight(label.getText()) / 2));
        section.addComponent(colonySection).inTL(0, 0);
        section.addUIElement(headerTooltip).inTL(width * 0.7f + 5, 0);
        return section;
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
        if (checkSectorCurrentEconomyData != null && checkSectorCurrentEconomyData.isChecked()) {
            checkSectorCurrentEconomyData.setChecked(false);
            AshMisc.initPopUpDialog(
                    new CommodityDetailDialog(
                            Global.getSector().getFaction(factionId), commodityId),
                    AoTDDetailedCommodityUITable.getWidth() + 38,
                    665);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
