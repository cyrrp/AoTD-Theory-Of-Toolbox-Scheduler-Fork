package data.kaysaar.aotd.tot.listeners;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.Market;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;
import data.kaysaar.aotd.tot.listeners.ui.AoTDPointerToStarSystem;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AoTDPriceTableRemoval implements ExtendedUIPanelPlugin {

    private static final int PRICE_QUANTITY = 500;
    private static final int DISPLAY_LIMIT = 5;

    TooltipMakerAPI originalTooltip;
    CustomPanelAPI mainPanel;
    boolean removed = false;
    float yAdded;
    String commodityId;
    float prevY;

    public AoTDPriceTableRemoval(TooltipMakerAPI tooltipMakerAPI, String commodityId) {
        this.originalTooltip = tooltipMakerAPI;
        this.commodityId = commodityId;
        this.mainPanel = Global.getSettings().createCustom(1, 1, this);

        yAdded = originalTooltip.getHeightSoFar();

        final PositionAPI pos = originalTooltip.getPosition();
        prevY = (int) (pos.getY() + pos.getHeight());
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (removed) return;
        if (!(originalTooltip.getPrev() instanceof LabelAPI)) return;

        LabelAPI label = (LabelAPI) originalTooltip.getPrev();
        if (!label.getText().contains("Per unit prices assume")) return;

        rebuildTradeTooltip();
    }

    private void rebuildTradeTooltip() {
        UIPanelAPI holder = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(originalTooltip).get(0);
        List<UIComponentAPI> comps = ReflectionUtilis.getChildrenCopy(holder);

        ArrayList<SellRowData> sellRows = new ArrayList<>();
        ArrayList<BuyRowData> buyRows = new ArrayList<>();

        buildRowsFromCache(sellRows, buyRows);

        sellRows.sort(
                new Comparator<SellRowData>() {
                    @Override
                    public int compare(SellRowData a, SellRowData b) {
                        int priceCompare = Integer.compare(b.pricePerUnit, a.pricePerUnit);
                        if (priceCompare != 0) return priceCompare;

                        return Integer.compare(b.demand, a.demand);
                    }
                });

        buyRows.sort(
                new Comparator<BuyRowData>() {
                    @Override
                    public int compare(BuyRowData a, BuyRowData b) {
                        int priceCompare = Integer.compare(a.pricePerUnit, b.pricePerUnit);
                        if (priceCompare != 0) return priceCompare;

                        return Integer.compare(b.availableForSort, a.availableForSort);
                    }
                });

        removeVanillaRows(holder, comps, sellRows.isEmpty(), buyRows.isEmpty());

        removed = true;
        originalTooltip.addSpacer(0f).getPosition().inTL(5, yAdded);
        originalTooltip.setHeightSoFar(yAdded);

        if (!sellRows.isEmpty()) {
            addSellTable(sellRows);
        }

        if (!buyRows.isEmpty()) {
            addBuyTable(buyRows);
        }

        if (sellRows.isEmpty() && buyRows.isEmpty()) {
            originalTooltip.addPara("No trade data!", Misc.getGrayColor(), 10f);
        } else {
            addFootnotes();
        }

        repositionTooltip();
    }

    private void buildRowsFromCache(
            ArrayList<SellRowData> sellRows, ArrayList<BuyRowData> buyRows) {
        AoTDTradePriceCache.CandidateSet candidates =
                AoTDTradePriceCache.getCandidates(commodityId);

        for (AoTDTradePriceCache.Candidate candidate : candidates.sellCandidates) {
            MarketAPI market = candidate.getMarket();
            if (market == null || market.isHidden()) continue;

            AoTDCommodityOnMarket com =
                    AoTDCommodityOnMarket.getComMarketInstanceSave(market, commodityId);
            if (com == null) continue;

            int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();
            if (demand <= 0) continue;

            SellRowData sell = new SellRowData();
            sell.market = market;
            sell.pricePerUnit = getSellPricePerUnit(market, commodityId);
            sell.demand = demand;
            sell.deficit = com.getDeficitQuantity();
            sellRows.add(sell);
        }

        for (AoTDTradePriceCache.Candidate candidate : candidates.buyCandidates) {
            MarketAPI market = candidate.getMarket();
            if (market == null || market.isHidden()) continue;
            if (!(market instanceof Market)) continue;

            AoTDCommodityOnMarket com =
                    AoTDCommodityOnMarket.getComMarketInstanceSave(market, commodityId);
            if (com == null) continue;

            int supply = com.getSupplyDemandData().getTotalRawUnitsFromSupply();
            int stableAvailable =
                    (int)
                            AoTdMainWorkTask2.getAoTDStableSharedSubmarketLimit(
                                    (Market) market, com, supply);
            int liveAvailable = AoTDOpenMarketPlugin.getStockPileToolbox(com);

            // The table displays liveAvailable, so a market with liveAvailable == 0 must never
            // appear
            // even if the stable shared limit is still positive due to economy/cache state.
            if (stableAvailable <= 0 || liveAvailable <= 0) continue;

            int effectiveAvailable = Math.min(stableAvailable, liveAvailable);
            if (effectiveAvailable <= 0) continue;

            BuyRowData buy = new BuyRowData();
            buy.market = market;
            buy.pricePerUnit = getBuyPricePerUnit(market, commodityId);
            buy.availableForSort = effectiveAvailable;
            buy.availableDisplay = liveAvailable;
            buy.excess = com.getExcessQuantity();
            buyRows.add(buy);
        }
    }

    private void removeVanillaRows(
            UIPanelAPI holder, List<UIComponentAPI> comps, boolean noSellRows, boolean noBuyRows) {
        int toRemove = 6;

        if (noSellRows) {
            toRemove -= 2;
        }
        if (noBuyRows) {
            toRemove -= 2;
        }

        toRemove = Math.min(toRemove, comps.size());

        for (int i = 0; i < toRemove; i++) {
            int index = comps.size() - 1 - i;
            holder.removeComponent(comps.get(index));
        }
    }

    private void addSellTable(ArrayList<SellRowData> rows) {
        originalTooltip.addPara("Best places to sell:", 10f);
        originalTooltip.beginTable(
                Global.getSector().getPlayerFaction(),
                20f,
                "Price / 500*",
                100,
                "Demand",
                70,
                "Deficit",
                70,
                "Location",
                230,
                "Star System",
                140,
                "Dist (LY)",
                80);

        int max = Math.min(DISPLAY_LIMIT, rows.size());
        for (int i = 0; i < max; i++) {
            SellRowData rowData = rows.get(i);
            MarketAPI market = rowData.market;

            String deficitString = "---";
            Color deficitStrColor = Misc.getGrayColor();
            if (rowData.deficit > 0) {
                deficitString = Misc.getWithDGS(rowData.deficit);
                deficitStrColor = Misc.getNegativeHighlightColor();
            }

            MarketDisplayData display = getMarketDisplayData(market);

            Object row =
                    originalTooltip.addRow(
                            Color.ORANGE,
                            Misc.getDGSCredits(rowData.pricePerUnit),
                            Color.ORANGE,
                            Misc.getWithDGS(rowData.demand),
                            deficitStrColor,
                            deficitString,
                            Alignment.LMID,
                            market.getFaction().getBaseUIColor(),
                            market.getName() + " - " + display.factionName,
                            display.locationColor,
                            display.location,
                            Color.ORANGE,
                            Misc.getRoundedValueMaxOneAfterDecimal(display.distanceLY));

            attachStarSystemPointer(row, market, display.locationColor);
        }

        originalTooltip.addTable("", 0, 10f);
    }

    private void addBuyTable(ArrayList<BuyRowData> rows) {
        originalTooltip.addPara("Best places to buy:", 10f);
        originalTooltip.beginTable(
                Global.getSector().getPlayerFaction(),
                20f,
                "Price / 500*",
                100,
                "Available",
                70,
                "Excess",
                70,
                "Location",
                230,
                "Star System",
                140,
                "Dist (LY)",
                80);

        int max = Math.min(DISPLAY_LIMIT, rows.size());
        for (int i = 0; i < max; i++) {
            BuyRowData rowData = rows.get(i);
            MarketAPI market = rowData.market;

            String excessString = "---";
            Color excessColor = Misc.getGrayColor();
            if (rowData.excess > 0) {
                excessString = Misc.getWithDGS(rowData.excess);
                excessColor = Misc.getPositiveHighlightColor();
            }

            MarketDisplayData display = getMarketDisplayData(market);

            Object row =
                    originalTooltip.addRow(
                            Color.ORANGE,
                            Misc.getDGSCredits(rowData.pricePerUnit),
                            Color.ORANGE,
                            Misc.getWithDGS(rowData.availableDisplay),
                            excessColor,
                            excessString,
                            Alignment.LMID,
                            market.getFaction().getBaseUIColor(),
                            market.getName() + " - " + display.factionName,
                            display.locationColor,
                            display.location,
                            Color.ORANGE,
                            Misc.getRoundedValueMaxOneAfterDecimal(display.distanceLY));

            attachStarSystemPointer(row, market, display.locationColor);
        }

        originalTooltip.addTable("", 0, 10f);
    }

    private void addFootnotes() {
        originalTooltip.addPara(
                "*All values approximate. Prices do not include tariffs, which can be avoided through black market trade.",
                Misc.getGrayColor(),
                5f);

        originalTooltip.addPara(
                "*Per-unit prices assume buying or selling a batch of %s units. Each unit bought costs more as the market’s supply is reduced, and each unit sold brings in less as demand is fulfilled.",
                5f, Misc.getGrayColor(), Color.ORANGE, String.valueOf(PRICE_QUANTITY));

        originalTooltip.addPara(
                "*Deficit and excess values may change next month due to trade events, so they should be considered reliable only for the current month.",
                Misc.getGrayColor(),
                5f);
    }

    private void repositionTooltip() {
        final PositionAPI posit = originalTooltip.getPosition();
        final int prevX = (int) posit.getX();

        ReflectionUtilis.invokeStaticMethodWithAutoProjection(
                StandardTooltipV2Expandable.class, "updateSizeAsUIElement", originalTooltip);

        posit.inBL(0f, 0f);

        final float currX = posit.getX();
        final float currY = posit.getY() + posit.getHeight();

        posit.inBL(prevX - currX, Math.max(prevY - currY, 30));
    }

    private static MarketDisplayData getMarketDisplayData(MarketAPI market) {
        MarketDisplayData data = new MarketDisplayData();

        data.factionName = AoTDToolboxMisc.capitalizeFirst(market.getFaction().getDisplayName());
        data.location = "In Hyperspace";
        data.locationColor = Misc.getGrayColor();

        if (market.getStarSystem() != null) {
            StarSystemAPI system = market.getStarSystem();
            data.location = system.getBaseName();

            PlanetAPI star = system.getStar();
            if (star != null) {
                data.locationColor = star.getSpec().getIconColor();
            }
        }

        data.distanceLY = Misc.getDistanceToPlayerLY(market.getPrimaryEntity());

        return data;
    }

    private static void attachStarSystemPointer(
            final Object row, final MarketAPI market, final Color locationColor) {
        ReflectionUtilis.invokeMethodWithAutoProjection(
                "setAfterCreate",
                row,
                new Runnable() {
                    @Override
                    public void run() {
                        AoTDPointerToStarSystem pointer =
                                new AoTDPointerToStarSystem(
                                        (Float) ReflectionUtilis.invokeMethod("getHeight", row),
                                        market.getLocationInHyperspace(),
                                        locationColor);

                        Object columns =
                                ReflectionUtilis.invokeMethodWithAutoProjection("getCol", row, 4);
                        PositionAPI pos =
                                (PositionAPI)
                                        ReflectionUtilis.invokeMethodWithAutoProjection(
                                                "addComponent", columns, pointer.getMainPanel());

                        pos.inRMid(5f);
                    }
                });
    }

    private static int getBuyPricePerUnit(MarketAPI market, String commodityId) {
        return Math.round(market.getSupplyPrice(commodityId, getQuantity(), true) / getQuantity());
    }

    private static int getSellPricePerUnit(MarketAPI market, String commodityId) {
        return Math.round(market.getDemandPrice(commodityId, getQuantity(), true) / getQuantity());
    }

    public static int getQuantity() {
        return PRICE_QUANTITY;
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {}

    @Override
    public void clearUI() {}

    private static class SellRowData {
        MarketAPI market;
        int pricePerUnit;
        int demand;
        int deficit;
    }

    private static class BuyRowData {
        MarketAPI market;
        int pricePerUnit;
        int availableForSort;
        int availableDisplay;
        int excess;
    }

    private static class MarketDisplayData {
        String factionName;
        String location;
        Color locationColor;
        float distanceLY;
    }
}
