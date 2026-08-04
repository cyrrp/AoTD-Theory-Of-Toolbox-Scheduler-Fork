package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.EntityRenderer;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EntityRowComponent implements ExtendedUIPanelPlugin {

    protected StarSystemAPI system;
    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;

    protected float width;
    protected float height;

    protected float defaultIconSize = 30;
    protected float separator = 3f;

    public EntityRowComponent(StarSystemAPI system, float width, float height) {
        this.system = system;
        this.width = width;
        this.height = height;

        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        clearUI();

        contentPanel = Global.getSettings().createCustom(width, height, null);

        TooltipMakerAPI tooltip = contentPanel.createUIElement(width, height, false);

        List<MarketAPI> markets = getMarketsToDisplay();

        if (markets.isEmpty()) {
            contentPanel.addUIElement(tooltip).inTL(0f, 0f);
            mainPanel.addComponent(contentPanel).inTL(0f, 0f);
            return;
        }

        float iconSize = Math.min(defaultIconSize, height);

        float separatorTotal = separator * Math.max(0, markets.size() - 1);
        float iconWidthTotal = iconSize * markets.size();
        float availableWidth = width - separatorTotal;

        if (iconWidthTotal > availableWidth && availableWidth > 0f) {
            float scale = availableWidth / iconWidthTotal;
            iconSize *= scale;
        }

        float startingX = 0f;
        float startingY = Math.max(0f, (height - iconSize) / 2f);

        for (MarketAPI market : markets) {
            SectorEntityToken entity = market.getPrimaryEntity();

            if (entity == null) {
                continue;
            }

            EntityRenderer renderer = new EntityRenderer(entity, iconSize);

            tooltip.addCustom(renderer.getMainPanel(), 0f).getPosition().inTL(startingX, startingY);
            // TODO - Market Info on hover
            tooltip.addTooltipToPrevious(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return false;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 350f;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            tooltip.addTitle(market.getName());
                        }
                    },
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);

            startingX += iconSize + separator;
        }

        contentPanel.addUIElement(tooltip).inTL(0f, 0f);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }

    protected List<MarketAPI> getMarketsToDisplay() {
        List<MarketAPI> markets = new ArrayList<>();

        if (system == null) {
            return markets;
        }

        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
            if (market == null) continue;
            if (market.getPrimaryEntity() == null) continue;
            if (market.isHidden()) continue;

            markets.add(market);
        }

        markets.sort(
                new Comparator<MarketAPI>() {
                    @Override
                    public int compare(MarketAPI o1, MarketAPI o2) {
                        int sizeCompare = Integer.compare(o2.getSize(), o1.getSize());
                        if (sizeCompare != 0) {
                            return sizeCompare;
                        }

                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                });

        return markets;
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }
    }

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
