package data.kaysaar.aotd.tot.ui.commoditypanel;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import java.awt.*;
import java.util.*;
import java.util.List;

public class AoTDCommodityShortPanelCombined implements ExtendedUIPanelPlugin {

    private final CustomPanelAPI mainPanel;
    boolean contractMet = true;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public AoTDCommodityShortPanelCombined(
            float width, int columns, Industry ind, boolean demand, boolean ignoreDemand) {
        float ySeparator = 3f;
        float xGap = 2f;

        List<MutableCommodityQuantity> list = demand ? ind.getAllDemand() : ind.getAllSupply();

        int count = list.size();
        int rows = Math.max(0, (int) Math.ceil((double) count / columns));
        float height = (rows * AoTDCommodityShortPanel.height) + ((rows - 1) * ySeparator);

        mainPanel = Global.getSettings().createCustom(width, height, this);
        if (height == 0) return;

        float cellWidth = (width - (columns - 1) * xGap) / columns;

        float currY = 0f;
        int index = 0;

        while (index < count) {

            int itemsThisRow = Math.min(columns, count - index);

            // center offset
            float totalRowWidth = itemsThisRow * cellWidth + (itemsThisRow - 1) * xGap;
            float startX = (width - totalRowWidth) * 0.5f;

            float currX = startX;

            for (int i = 0; i < itemsThisRow; i++) {
                MutableCommodityQuantity entry = list.get(index);

                CustomPanelAPI commodityPanel;

                if (demand) {
                    commodityPanel =
                            new AoTDCommodityShortPanel(
                                            entry.getCommodityId(),
                                            AoTDCommodityEconSpecManager.getEconSpec(
                                                            entry.getCommodityId())
                                                    .getCalculationScript()
                                                    .getRawUnitsFromDemand(
                                                            entry.getQuantity(),
                                                            ind.getMarket(),
                                                            entry.getCommodityId(),
                                                            ind),
                                            Color.ORANGE,
                                            cellWidth,
                                            ind,
                                            true,
                                            ignoreDemand)
                                    .getMainPanel();
                } else {
                    commodityPanel =
                            new AoTDCommodityShortPanel(
                                            entry.getCommodityId(),
                                            AoTDCommodityEconSpecManager.getEconSpec(
                                                            entry.getCommodityId())
                                                    .getCalculationScript()
                                                    .getRawUnitsFromSupply(
                                                            entry.getQuantity(),
                                                            ind.getMarket(),
                                                            entry.getCommodityId(),
                                                            ind),
                                            Misc.getPositiveHighlightColor(),
                                            cellWidth)
                                    .getMainPanel();
                }

                mainPanel.addComponent(commodityPanel).inTL(currX, currY);

                currX += cellWidth + xGap;
                index++;
            }

            currY += AoTDCommodityShortPanel.height + ySeparator;
        }
    }

    public AoTDCommodityShortPanelCombined(
            float width, int columns, ArrayList<Pair<String, Integer>> commodities) {
        float ySeparator = 3f;
        float xGap = 2f;

        List<Pair<String, Integer>> list = commodities.stream().toList();

        int count = list.size();
        int rows = Math.max(0, (int) Math.ceil((double) count / columns));
        float height = (rows * AoTDCommodityShortPanel.height) + ((rows - 1) * ySeparator);

        mainPanel = Global.getSettings().createCustom(width, height, this);
        if (height == 0) return;

        float cellWidth = (width - (columns - 1) * xGap) / columns;

        float currY = 0f;
        int index = 0;

        while (index < count) {

            int itemsThisRow = Math.min(columns, count - index);

            // center offset
            float totalRowWidth = itemsThisRow * cellWidth + (itemsThisRow - 1) * xGap;
            float startX = (width - totalRowWidth) * 0.5f;

            float currX = startX;

            for (int i = 0; i < itemsThisRow; i++) {
                Pair<String, Integer> entry = list.get(index);

                CustomPanelAPI commodityPanel;
                commodityPanel =
                        new AoTDCommodityShortPanel(
                                        entry.one, entry.two, Color.ORANGE, cellWidth, true)
                                .getMainPanel();

                mainPanel.addComponent(commodityPanel).inTL(currX, currY);

                currX += cellWidth + xGap;
                index++;
            }

            currY += AoTDCommodityShortPanel.height + ySeparator;
        }
    }

    public AoTDCommodityShortPanelCombined(
            float width, int columns, LinkedHashMap<String, Integer> commodities) {
        float ySeparator = 3f;
        float xGap = 2f;

        List<Pair<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : commodities.entrySet()) {
            list.add(new Pair<>(entry.getKey(), entry.getValue()));
        }

        int count = list.size();
        int rows = Math.max(0, (int) Math.ceil((double) count / columns));
        float height = (rows * AoTDCommodityShortPanel.height) + ((rows - 1) * ySeparator);

        mainPanel = Global.getSettings().createCustom(width, height, this);
        if (height == 0) return;

        float cellWidth = (width - (columns - 1) * xGap) / columns;

        float currY = 0f;
        int index = 0;

        while (index < count) {

            int itemsThisRow = Math.min(columns, count - index);

            // center offset
            float totalRowWidth = itemsThisRow * cellWidth + (itemsThisRow - 1) * xGap;
            float startX = (width - totalRowWidth) * 0.5f;

            float currX = startX;

            for (int i = 0; i < itemsThisRow; i++) {
                Pair<String, Integer> entry = list.get(index);

                CustomPanelAPI commodityPanel;
                commodityPanel =
                        new AoTDCommodityShortPanel(
                                        entry.one, entry.two, Color.ORANGE, cellWidth, true)
                                .getMainPanel();

                mainPanel.addComponent(commodityPanel).inTL(currX, currY);

                currX += cellWidth + xGap;
                index++;
            }

            currY += AoTDCommodityShortPanel.height + ySeparator;
        }
    }

    public AoTDCommodityShortPanelCombined(
            float width,
            int columns,
            AoTDTradeContract tradeContract,
            boolean shortMode,
            boolean ignoreDemand) {
        float ySeparator = 3f;
        float xGap = 5f;
        float cellWidth = 100; // fixed width

        int max = Math.floorDiv((int) width, (int) cellWidth);

        int count = tradeContract.getContractData().size();
        List<AoTDTradeContract.TradeContractData> dataList =
                tradeContract.getContractData().values().stream().toList();

        int rows = Math.max(0, (int) Math.ceil((double) count / columns));
        float height = (rows * AoTDCommodityShortPanel.height) + ((rows - 1) * ySeparator);
        if (!shortMode) {
            cellWidth = (width - (columns - 1) * xGap) / columns;
        }
        mainPanel = Global.getSettings().createCustom(width, height, this);
        if (height == 0) return;

        float currY = 0f;
        int index = 0;

        while (index < count) {
            int itemsThisRow = Math.min(columns, count - index);
            if (shortMode) {
                itemsThisRow = Math.min(columns, max);
            }

            // calculate total row width based only on spacing + fixed cell width
            float totalRowWidth = (itemsThisRow * cellWidth) + ((itemsThisRow - 1) * xGap);

            // center the row
            float startX = (width - totalRowWidth) * 0.5f;
            float currX = startX;

            for (int i = 0; i < itemsThisRow; i++) {

                AoTDTradeContract.TradeContractData data = dataList.get(index);
                if (Global.getSettings().getCommoditySpec(data.getCommodityId()) == null) continue;
                if (shortMode) {
                    CustomPanelAPI commodityPanel =
                            new AoTDCommodityShortPanel(
                                            data.getCommodityId(),
                                            data.getReqMonthly(),
                                            Color.ORANGE,
                                            cellWidth,
                                            true,
                                            shortMode,
                                            ignoreDemand)
                                    .getMainPanel();
                    mainPanel.addComponent(commodityPanel).inTL(currX, currY);
                } else {
                    CustomPanelAPI commodityPanel =
                            new AoTDCommodityShortPanel(
                                            data.getCommodityId(),
                                            Color.ORANGE,
                                            cellWidth,
                                            data,
                                            tradeContract.getId(),
                                            ignoreDemand)
                                    .getMainPanel();
                    mainPanel.addComponent(commodityPanel).inTL(currX, currY);
                }

                currX += cellWidth + xGap;
                index++;
            }
            if (shortMode && index >= max && index < count) {
                TooltipMakerAPI tlDots =
                        mainPanel.createUIElement(30, mainPanel.getPosition().getHeight(), false);
                tlDots.addPara("...", 13f);
                mainPanel.addUIElement(tlDots).inTL(currX - 5, currY);
                break;
            }

            currY += AoTDCommodityShortPanel.height + ySeparator;
        }
    }

    @Override
    public void createUI() {}

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
