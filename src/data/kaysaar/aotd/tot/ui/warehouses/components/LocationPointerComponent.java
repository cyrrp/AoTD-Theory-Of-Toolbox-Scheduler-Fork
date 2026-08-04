package data.kaysaar.aotd.tot.ui.warehouses.components;

import ashlib.data.plugins.ui.EntityWithNameComponent;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.listeners.ui.AoTDPointerToStarSystem;
import java.awt.*;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class LocationPointerComponent implements ExtendedUIPanelPlugin {

    protected CustomPanelAPI mainPanel;
    protected CustomPanelAPI contentPanel;

    protected SectorEntityToken entity;
    protected Vector2f locationInHyperspace;
    protected Color pointerColor;

    protected float width;
    protected float height;

    protected float pointerSize = 30f;
    protected float distanceLabelHeight = 18f;
    protected float separator = 3f;

    protected boolean showMarketSize = true;

    protected LabelAPI distanceLabel;

    public LocationPointerComponent(SectorEntityToken entity, float width, float height) {
        this(entity, null, width, height, Misc.getBasePlayerColor(), true);
    }

    public LocationPointerComponent(
            MarketAPI market, float width, float height, Color pointerColor) {
        this(
                market.getStarSystem().getStar(),
                getLocationBetweenPlayerAndMarket(market),
                width,
                height,
                pointerColor,
                true);
    }

    public LocationPointerComponent(
            SectorEntityToken entity, float width, float height, boolean showMarketSize) {
        this(entity, null, width, height, Misc.getBasePlayerColor(), showMarketSize);
    }

    public LocationPointerComponent(
            SectorEntityToken entity, Vector2f locationInHyperspace, float width, float height) {
        this(entity, locationInHyperspace, width, height, Misc.getBasePlayerColor(), true);
    }

    public LocationPointerComponent(
            SectorEntityToken entity,
            Vector2f locationInHyperspace,
            float width,
            float height,
            Color pointerColor) {
        this(entity, locationInHyperspace, width, height, pointerColor, true);
    }

    public LocationPointerComponent(
            SectorEntityToken entity,
            Vector2f locationInHyperspace,
            float width,
            float height,
            Color pointerColor,
            boolean showMarketSize) {
        this.entity = entity;
        this.locationInHyperspace = locationInHyperspace;
        this.width = width;
        this.height = height;
        this.pointerColor = pointerColor == null ? Misc.getBasePlayerColor() : pointerColor;
        this.showMarketSize = showMarketSize;

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

        // Reset tooltip offset for manual positioning.
        tooltip.addSpacer(0f).getPosition().inTL(0f, 0f);

        float actualPointerSize = Math.min(pointerSize, Math.min(width, height));
        float entityHeight = height - actualPointerSize - distanceLabelHeight - separator * 2f;

        if (entityHeight < 0f) {
            entityHeight = 0f;
        }

        float y = 0f;

        if (entity != null && entityHeight > 0f) {
            EntityWithNameComponent entityComponent =
                    new EntityWithNameComponent(entity, width, entityHeight, showMarketSize);
            entityComponent.createUI();

            tooltip.addCustom(entityComponent.getMainPanel(), 0f).getPosition().inTL(0, y + 6);

            y += entityHeight + separator;
        }

        Vector2f target = getTargetLocationInHyperspace();

        AoTDPointerToStarSystem pointer =
                new AoTDPointerToStarSystem(actualPointerSize - 8, target, pointerColor);
        pointer.createUI();

        tooltip.addCustom(pointer.getMainPanel(), 0f)
                .getPosition()
                .inTL((width - (actualPointerSize - 8)) / 2f, y + 13);

        y += actualPointerSize + separator;

        distanceLabel =
                tooltip.addPara(
                        getDistanceText(), Misc.getTooltipTitleAndLightHighlightColor(), 0f);
        distanceLabel
                .getPosition()
                .inTL(width / 2 - (distanceLabel.computeTextWidth(distanceLabel.getText()) / 2), y);

        contentPanel.addUIElement(tooltip).inTL(0f, 0f);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
    }

    protected Vector2f getTargetLocationInHyperspace() {
        if (locationInHyperspace != null) {
            return locationInHyperspace;
        }

        if (entity != null) {
            return entity.getLocationInHyperspace();
        }

        return null;
    }

    protected String getDistanceText() {
        if (entity != null && locationInHyperspace == null) {
            return getDistanceText(Misc.getDistanceToPlayerLY(entity));
        }

        Vector2f target = getTargetLocationInHyperspace();

        if (target == null) {
            return "? LY";
        }

        return getDistanceText(Misc.getDistanceToPlayerLY(target));
    }

    protected String getDistanceText(float distanceLY) {
        if (distanceLY >= 99999f) {
            return "? LY";
        }

        if (distanceLY < 0.05f) {
            return "0 LY";
        }

        if (distanceLY < 10f) {
            return String.format("%.1f LY", distanceLY);
        }

        return String.format("%.0f LY", distanceLY);
    }

    @Override
    public void clearUI() {
        if (contentPanel != null) {
            mainPanel.removeComponent(contentPanel);
            contentPanel = null;
        }

        distanceLabel = null;
    }

    @Override
    public void positionChanged(PositionAPI position) {}

    @Override
    public void renderBelow(float alphaMult) {}

    @Override
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {
        if (distanceLabel != null) {
            distanceLabel.setText(getDistanceText());
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {}

    public static Vector2f getLocationBetweenPlayerAndMarket(MarketAPI market) {
        if (market == null) {
            return null;
        }

        if (market.getPrimaryEntity() == null) {
            return null;
        }

        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) {
            return market.getPrimaryEntity().getLocationInHyperspace();
        }

        Vector2f playerLoc = Global.getSector().getPlayerFleet().getLocationInHyperspace();
        Vector2f marketLoc = market.getPrimaryEntity().getLocationInHyperspace();

        if (playerLoc == null || marketLoc == null) {
            return marketLoc;
        }

        return new Vector2f((playerLoc.x + marketLoc.x) / 2f, (playerLoc.y + marketLoc.y) / 2f);
    }

    @Override
    public void buttonPressed(Object buttonId) {}
}
