package data.kaysaar.aotd.tot.ui.economy.tradecontracts.browser;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import ashlib.data.plugins.ui.models.resizable.ImageViewer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import java.util.List;

public class IntrestedInCommodities implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;

    public IntrestedInCommodities(
            float width, float iconSize, Alignment alignment, String... commodities) {

        final float sepX = 10f; // spacing between icons (x)
        final float sepY = 5f; // spacing between rows (y)

        int count = commodities == null ? 0 : commodities.length;

        int perRow = Math.max(1, (int) Math.floor((width + sepX) / (iconSize + sepX)));
        int rows = count == 0 ? 1 : (int) Math.ceil(count / (float) perRow);

        float height = rows * iconSize + (rows - 1) * sepY;

        mainPanel = Global.getSettings().createCustom(width, height, this);

        for (int i = 0; i < count; i++) {

            int row = i / perRow;
            int col = i % perRow;

            int remaining = count - row * perRow;
            int iconsThisRow = Math.min(perRow, remaining);

            float rowWidth = iconsThisRow * iconSize + (iconsThisRow - 1) * sepX;

            float startX;

            switch (alignment) {
                case TL:
                    startX = 0f;
                    break;

                case TR:
                    startX = width - rowWidth;
                    break;

                case MID:
                default:
                    startX = (width - rowWidth) * 0.5f;
                    break;
            }

            float x = startX + col * (iconSize + sepX);
            float y = row * (iconSize + sepY);

            String commodityId = commodities[i];
            String icon = Global.getSettings().getCommoditySpec(commodityId).getIconName();

            ImageViewer viewer = new ImageViewer(iconSize, iconSize, icon);

            mainPanel.addComponent(viewer.getComponentPanel()).inTL(x, y);
        }
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
    public void render(float alphaMult) {}

    @Override
    public void advance(float amount) {}

    @Override
    public void processInput(List<InputEventAPI> events) {}

    @Override
    public void buttonPressed(Object buttonId) {}
}
