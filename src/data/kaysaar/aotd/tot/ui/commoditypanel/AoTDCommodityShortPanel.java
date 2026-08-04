package data.kaysaar.aotd.tot.ui.commoditypanel;

import static com.fs.starfarer.api.util.Misc.getWithDGS;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;

public class AoTDCommodityShortPanel implements ExtendedUIPanelPlugin {
    String commodityId;
    int number;
    Color textColor;
    float width;
    CustomPanelAPI mainPanel;
    CustomPanelAPI componentPanel;
    boolean demandMode = false;
    Industry market;
    public static float height = 40;
    public boolean shortMode = false;
    public boolean ignoreDeficit = false;
    public Alignment al = Alignment.TR;
    AoTDTradeContract.TradeContractData contract;
    String contractId;
    boolean ignoreDemand = false;
    public SpriteAPI sprite = Global.getSettings().getSprite("rendering", "GlitchSquare");

    public AoTDCommodityShortPanel(String commodityId, int number, Color textColor, float width) {
        this.commodityId = commodityId;
        this.number = number;
        this.textColor = textColor;
        this.width = width;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public AoTDCommodityShortPanel(
            String commodityId, int number, Color textColor, float width, boolean demandMode) {
        this.commodityId = commodityId;
        this.number = number;
        this.textColor = textColor;
        this.width = width;
        this.demandMode = demandMode;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public AoTDCommodityShortPanel(
            String commodityId,
            int number,
            Color textColor,
            float width,
            Industry market,
            boolean demandMode,
            boolean ignoreDeficit) {
        this.commodityId = commodityId;
        this.number = number;
        this.textColor = textColor;
        this.width = width;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.market = market;
        this.ignoreDeficit = ignoreDeficit;
        this.demandMode = demandMode;
        createUI();
    }

    public AoTDCommodityShortPanel(
            String commodityId,
            int number,
            Color textColor,
            float width,
            boolean demandMode,
            boolean shortMode) {
        this.commodityId = commodityId;
        this.number = number;
        this.textColor = textColor;
        this.width = width;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.demandMode = demandMode;
        this.shortMode = shortMode;
        if (shortMode) {

            al = Alignment.TL;
        }
        createUI();
    }

    public AoTDCommodityShortPanel(
            String commodityId,
            int number,
            Color textColor,
            float width,
            boolean demandMode,
            boolean shortMode,
            boolean ignoreDemand) {
        this.commodityId = commodityId;
        this.number = number;
        this.textColor = textColor;
        this.width = width;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.demandMode = demandMode;
        this.ignoreDemand = ignoreDemand;
        this.shortMode = shortMode;
        if (shortMode) {

            al = Alignment.TL;
        }
        createUI();
    }

    public AoTDCommodityShortPanel(
            String commodityId,
            Color textColor,
            float width,
            AoTDTradeContract.TradeContractData contract,
            String contractId,
            boolean ignoreDemand) {
        this.commodityId = commodityId;
        this.textColor = textColor;
        this.contract = contract;
        this.width = width;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        this.demandMode = true;
        this.shortMode = false;
        this.contractId = contractId;
        this.ignoreDemand = ignoreDemand;
        if (shortMode) {

            al = Alignment.TL;
        }
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    private static final DecimalFormat SHORT_FORMAT = new DecimalFormat("0.##");

    public static String getShortDGS(float value) {
        if (value == 0f) return "0";

        float abs = Math.abs(value);
        String suffix = "";
        float scaled = abs;

        if (abs >= 1_000_000_000f) {
            scaled = abs / 1_000_000_000f;
            suffix = "B";
        } else if (abs >= 1_000_000f) {
            scaled = abs / 1_000_000f;
            suffix = "M";
        } else if (abs >= 1_000f) {
            scaled = abs / 1_000f;
            suffix = "k";
        } else {
            return getWithDGS(value); // fallback to normal formatting
        }

        String formatted = SHORT_FORMAT.format(scaled);
        return (value < 0 ? "-" : "") + formatted + suffix;
    }

    private String fmt(float v) {
        return shortMode ? getShortDGS(v) : getWithDGS(v);
    }

    @Override
    public void createUI() {
        if (componentPanel != null) {
            mainPanel.removeComponent(componentPanel);
        }

        componentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);

        float iconSize = componentPanel.getPosition().getHeight();
        float tooltipW = componentPanel.getPosition().getWidth() - iconSize - 5f;

        TooltipMakerAPI tooltip = componentPanel.createUIElement(tooltipW, iconSize, false);
        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);

        ImageViewer viewer = new ImageViewer(iconSize, iconSize, spec.getIconName());
        componentPanel.addComponent(viewer.getComponentPanel()).inTL(0, 0);

        if (!demandMode) {
            tooltip.addPara(AoTDToolboxMisc.getDGSStringWithSign(number), textColor, 13f)
                    .setAlignment(al);

        } else if (market != null) {
            int def = market.getMaxDeficit(commodityId).two;

            if (def > 0 && !ignoreDeficit) {
                MutableStat stat = new MutableStat(def);
                int min =
                        Math.min(
                                market.getMarket()
                                        .getCommodityData(commodityId)
                                        .getDeficitQuantity(),
                                AoTDCommodityEconSpecManager.getEconSpec(commodityId)
                                        .getCalculationScript()
                                        .getRawUnitsFromDemand(
                                                stat, market.getMarket(), commodityId, market));

                tooltip.addPara(fmt(number), Color.ORANGE, 2f).setAlignment(al);
                tooltip.addPara(fmt(-min), Misc.getNegativeHighlightColor(), 6f).setAlignment(al);

            } else {
                tooltip.addPara(fmt(number), textColor, 13f).setAlignment(al);
            }

        } else if (contract != null && !shortMode) {
            int def =
                    AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                                    commodityId, Factions.PLAYER)
                            - AoTDSectorProductionDemandDataUtils
                                    .getTotalDemandFromFactionTillContract(
                                            commodityId, Factions.PLAYER, contractId);
            def *= -1;
            def = Math.min(def, contract.getReqMonthly());
            if (def > 0 && !ignoreDemand) {
                tooltip.addPara(fmt(contract.getReqMonthly()), Color.ORANGE, 2f).setAlignment(al);
                tooltip.addPara(fmt(-def), Misc.getNegativeHighlightColor(), 6f).setAlignment(al);
            } else {
                tooltip.addPara(fmt(contract.getReqMonthly()), textColor, 13f).setAlignment(al);
            }

        } else {
            tooltip.addPara(fmt(number), textColor, 13f).setAlignment(al);
        }

        componentPanel.addUIElement(tooltip).inTL(iconSize + 5f, 0);
        mainPanel.addComponent(componentPanel).inTL(0, 0);
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
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
