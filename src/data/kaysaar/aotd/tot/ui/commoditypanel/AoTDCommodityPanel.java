package data.kaysaar.aotd.tot.ui.commoditypanel;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.ui.commodityDetailedInfo.AoTDDetailedCommodityPanelContent;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AoTDCommodityPanel implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;
    AoTDCommodityUITable table;
    SpriteAPI blackBG = Global.getSettings().getSprite("rendering", "GlitchSquare");
    MarketAPI market;
    boolean isInDialog = false;
    Object mainColonyPanel;
    AoTDDetailedCommodityPanelContent parent;
    ButtonAPI shortageButton;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public AoTDCommodityPanel(float width, float height, MarketAPI market, boolean isInDialog) {
        this.market = market;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        this.isInDialog = isInDialog;
        createUI();
    }

    public void setMainColonyPanel(Object mainColonyPanel) {
        this.mainColonyPanel = mainColonyPanel;
    }

    public AoTDCommodityPanel(
            float width,
            float height,
            MarketAPI market,
            boolean isInDialog,
            AoTDDetailedCommodityPanelContent parent) {
        this.market = market;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        this.isInDialog = isInDialog;
        this.parent = parent;
        createUI();
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            if (table != null) {
                contentPanel.removeComponent(table.mainPanel);
            }
            mainPanel.removeComponent(contentPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        ArrayList<AoTDCommodityOnMarket> commodities = new ArrayList<>();
        for (CommodityOnMarketAPI commodityOnMarketAPI : market.getCommoditiesCopy()) {
            if (commodityOnMarketAPI instanceof AoTDCommodityOnMarket com) {
                com.getSupplyDemandData().updateSupplyDemandData(market);
                commodities.add(com);
            }
        }
        Collections.sort(
                commodities,
                new Comparator<AoTDCommodityOnMarket>() {
                    @Override
                    public int compare(AoTDCommodityOnMarket o1, AoTDCommodityOnMarket o2) {
                        int sup1 = o1.getSupplyDemandData().getTotalRawUnitsFromSupply();
                        int sup2 = o2.getSupplyDemandData().getTotalRawUnitsFromSupply();

                        if (sup1 != 0 && sup2 == 0) {
                            return -1;
                        } else if (sup1 == 0 && sup2 != 0) {
                            return 1;
                        } else {
                            return (int)
                                    Math.signum(
                                            o1.getSpec().getEconomyTier()
                                                    - o2.getSpec().getEconomyTier());
                        }
                    }
                });
        Iterator<AoTDCommodityOnMarket> commoditiesIterator = commodities.iterator();
        while (commoditiesIterator.hasNext()) {
            AoTDCommodityOnMarket com = commoditiesIterator.next();
            if (!com.getSpec().isNonEcon() && com.getSpec().isPrimary()) {
                if (!(com.getSupplyDemandData().doesHaveSupplyOrDemand())) {
                    commoditiesIterator.remove();
                }
            } else {
                commoditiesIterator.remove();
            }
        }

        TooltipMakerAPI tooltipHeader =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 40, false);
        float minusHeight = 45;
        if (!isInDialog) {
            minusHeight = 70;
        }
        if (table != null) {
            table.recreateTable();
        } else {
            table =
                    new AoTDCommodityUITable(
                            contentPanel.getPosition().getWidth(),
                            contentPanel.getPosition().getHeight() - minusHeight,
                            true,
                            0,
                            0,
                            commodities,
                            market,
                            isInDialog,
                            parent);
            table.createSections();
            table.createTable();
        }
        if (isInDialog) {
            tooltipHeader.addSectionHeading(
                    market.getName() + " Commodity Data",
                    market.getFaction().getBaseUIColor(),
                    market.getFaction().getDarkUIColor(),
                    Alignment.MID,
                    0f);

        } else {
            shortageButton =
                    tooltipHeader.addAreaCheckbox(
                            "Use stockpiles during shortage",
                            null,
                            market.getFaction().getBaseUIColor(),
                            market.getFaction().getDarkUIColor(),
                            market.getFaction().getBrightUIColor(),
                            contentPanel.getPosition().getWidth(),
                            20,
                            0f);
            shortageButton.setEnabled(
                    market.isPlayerOwned()
                            || market.getFaction().isPlayerFaction()
                            || Global.getSettings().isDevMode());
            shortageButton.getPosition().inTL(0, 0);
            if (market.isUseStockpilesForShortages()) {
                shortageButton.highlight();
            }
            tooltipHeader
                    .addSectionHeading(
                            "Market Commodity Data",
                            market.getFaction().getBaseUIColor(),
                            market.getFaction().getDarkUIColor(),
                            Alignment.MID,
                            5f)
                    .getPosition()
                    .inTL(0, 23);
        }
        contentPanel.addUIElement(tooltipHeader).inTL(0, 0);
        if (!isInDialog) {
            contentPanel.addComponent(table.mainPanel).inTL(0, 45);
        } else {
            contentPanel.addComponent(table.mainPanel).inTL(0, 20);
        }
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    @Override
    public void clearUI() {
        if (this.parent != null) {
            this.parent = null;
        }
        this.table.clearUI();
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {
        if (!isInDialog) {
            blackBG.setSize(
                    mainPanel.getPosition().getWidth(), mainPanel.getPosition().getHeight());
            blackBG.setColor(Color.black);
            blackBG.setAlphaMult(alphaMult * 0.7f);
            blackBG.renderAtCenter(
                    mainPanel.getPosition().getCenterX(), mainPanel.getPosition().getCenterY());
        }
    }

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (shortageButton != null) {
            if (shortageButton.isChecked()) {
                shortageButton.setChecked(false);
                market.setUseStockpilesForShortages(!market.isUseStockpilesForShortages());
                if (mainColonyPanel != null) {
                    ReflectionUtilis.invokeMethodWithAutoProjection(
                            "recreateWithEconUpdate", mainColonyPanel);
                }
            }
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
