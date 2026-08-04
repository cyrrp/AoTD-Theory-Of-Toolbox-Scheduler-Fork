package data.kaysaar.aotd.tot.listeners.ui;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class AoTDPointerToStarSystem implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    SpriteAPI arrow = Global.getSettings().getSprite("ui", "marketArrow");
    Vector2f location;
    Color color;

    public AoTDPointerToStarSystem(float iconSize, Vector2f locationInHyperspace, Color color) {
        mainPanel = Global.getSettings().createCustom(iconSize, iconSize, this);
        location = locationInHyperspace;
        this.color = color;
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
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
    public void render(float alphaMult) {
        Vector2f player = Global.getSector().getPlayerFleet().getLocationInHyperspace();
        Vector2f target = this.location;

        if (target != null) {
            if (player.equals(target)) {
                return;
            }
            float dx = target.x - player.x;
            float dy = target.y - player.y;

            // 0° = north
            float angle = (float) Math.toDegrees(Math.atan2(dx, dy));

            // normalize to 0–360 if needed
            if (angle < 0) angle += 360f;

            arrow.setSize(mainPanel.getPosition().getWidth(), mainPanel.getPosition().getHeight());
            arrow.setAngle(-angle);
            arrow.setColor(Misc.getBasePlayerColor());
            arrow.renderAtCenter(
                    mainPanel.getPosition().getCenterX(), mainPanel.getPosition().getCenterY());
        }
    }

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
