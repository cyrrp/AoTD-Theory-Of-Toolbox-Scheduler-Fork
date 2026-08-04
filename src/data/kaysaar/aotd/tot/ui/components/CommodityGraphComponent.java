package data.kaysaar.aotd.tot.ui.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommodityGraphComponent implements ExtendedUIPanelPlugin {

    CustomPanelAPI mainPanel;
    CustomPanelAPI contentPanel;

    int months;
    String commodityId;
    String factionId = Factions.HEGEMONY;

    boolean showAll = false;

    public CommodityGraphComponent(
            float width, float height, String commodityId, String factionId, int months) {
        mainPanel = Global.getSettings().createCustom(width, height, this);

        this.commodityId = commodityId;
        this.factionId = factionId;
        this.months = Math.max(1, months);

        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
        }

        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);

        ArrayList<Integer> prodData = getProductionHistory();
        ArrayList<Integer> demData = getDemandHistory();

        // Append current month as the newest point.
        // Important: these methods handle NEUTRAL correctly.
        prodData.add(getCurrentProduction());
        demData.add(getCurrentDemand());

        normalizeLengthFromEnd(prodData, demData);

        float highest = 0f;

        for (Integer value : prodData) {
            if (value != null && value > highest) {
                highest = value;
            }
        }

        for (Integer value : demData) {
            if (value != null && value > highest) {
                highest = value;
            }
        }

        float width = contentPanel.getPosition().getWidth();
        float height = contentPanel.getPosition().getHeight();

        ArrayList<Float> supplyYs =
                SupplyDemandAreaGraph.createSeriesForGraph(height, prodData, highest);
        ArrayList<Float> demandYs =
                SupplyDemandAreaGraph.createSeriesForGraph(height, demData, highest);

        SupplyDemandAreaGraph graph = new SupplyDemandAreaGraph(width, height, supplyYs, demandYs);

        graph.setColors(
                Misc.getPositiveHighlightColor().darker(),
                new Color(220, 155, 33),
                Misc.getNegativeHighlightColor().darker());

        graph.setAACrossCutPx(0f);
        graph.setCrossingOverlapPx(0f);
        graph.setAAFeatherPx(1.25f);

        contentPanel.addComponent(graph.getMainPanel()).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
    }

    private ArrayList<Integer> getProductionHistory() {
        if (Factions.NEUTRAL.equals(factionId)) {
            return getCombinedHistory(true);
        }

        AoTDFactionTradeData data = AoTDTradeManager.getInstance().getFactionTradeData(factionId);
        if (data == null) return new ArrayList<>();

        return safeCopy(data.getProductionFromMonths(months, commodityId));
    }

    private ArrayList<Integer> getDemandHistory() {
        if (Factions.NEUTRAL.equals(factionId)) {
            return getCombinedHistory(false);
        }

        AoTDFactionTradeData data = AoTDTradeManager.getInstance().getFactionTradeData(factionId);
        if (data == null) return new ArrayList<>();

        return safeCopy(data.getDemandFromMonths(months, commodityId));
    }

    private ArrayList<Integer> getCombinedHistory(boolean production) {
        ArrayList<Integer> result = new ArrayList<>();

        for (Map.Entry<String, AoTDFactionTradeData> entry :
                AoTDTradeManager.getInstance().getAllFactionTradeData().entrySet()) {

            String id = entry.getKey();
            AoTDFactionTradeData data = entry.getValue();

            if (id == null || data == null) continue;
            if (Factions.NEUTRAL.equals(id)) continue;

            ArrayList<Integer> values =
                    production
                            ? safeCopy(data.getProductionFromMonths(months, commodityId))
                            : safeCopy(data.getDemandFromMonths(months, commodityId));

            addAlignedFromEnd(result, values);
        }

        return result;
    }

    private ArrayList<Integer> safeCopy(List<Integer> source) {
        ArrayList<Integer> result = new ArrayList<>();
        if (source == null) return result;

        for (Integer value : source) {
            result.add(value == null ? 0 : value);
        }

        return result;
    }

    private void addAlignedFromEnd(ArrayList<Integer> target, List<Integer> source) {
        if (source == null || source.isEmpty()) return;

        while (target.size() < source.size()) {
            target.add(0, 0);
        }

        int offset = target.size() - source.size();

        for (int i = 0; i < source.size(); i++) {
            int value = source.get(i) == null ? 0 : source.get(i);
            int targetIndex = offset + i;

            target.set(targetIndex, target.get(targetIndex) + value);
        }
    }

    private void normalizeLengthFromEnd(ArrayList<Integer> prodData, ArrayList<Integer> demData) {
        int max = Math.max(prodData.size(), demData.size());

        while (prodData.size() < max) {
            prodData.add(0, 0);
        }

        while (demData.size() < max) {
            demData.add(0, 0);
        }

        // Graph needs at least two points to draw a segment.
        // If only one point exists, duplicate it instead of allowing a visual drop.
        if (max == 1) {
            prodData.add(prodData.get(0));
            demData.add(demData.get(0));
        }
    }

    private int getCurrentProduction() {
        if (!Factions.NEUTRAL.equals(factionId)) {
            return AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                    commodityId, factionId);
        }

        int total = 0;

        for (String id : AoTDTradeManager.getInstance().getAllFactionTradeData().keySet()) {
            if (id == null || Factions.NEUTRAL.equals(id)) continue;

            total +=
                    AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                            commodityId, id);
        }

        return total;
    }

    private int getCurrentDemand() {
        if (!Factions.NEUTRAL.equals(factionId)) {
            return AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(
                    commodityId, factionId);
        }

        int total = 0;

        for (String id : AoTDTradeManager.getInstance().getAllFactionTradeData().keySet()) {
            if (id == null || Factions.NEUTRAL.equals(id)) continue;

            total += AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(commodityId, id);
        }

        return total;
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
