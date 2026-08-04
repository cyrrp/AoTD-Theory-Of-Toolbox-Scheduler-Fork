package data.kaysaar.aotd.tot.ui.starsystems.stuctures;

import ashlib.data.plugins.misc.AshMisc;
import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;
import java.util.List;
import org.lwjgl.input.Keyboard;

public class StarSystemStructuresUI implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    StarSystemAPI system;

    public StarSystemStructuresUI(float width, float height, StarSystemAPI system) {
        this.system = system;
        this.mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    public void setSystem(StarSystemAPI system) {
        this.system = system;
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
        FactionAPI faction = AshMisc.getClaimingFaction(system.getCenter());

        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);

        TooltipMakerAPI tooltip =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight() - 30,
                        true);
        TooltipMakerAPI tooltipBottom =
                contentPanel.createUIElement(contentPanel.getPosition().getWidth(), 25, false);
        float separator = 10; // fixed
        float rowGap = 20; // vertical gap between rows (tweak)
        float availableWidth = contentPanel.getPosition().getWidth();

        float widgetW = StableStructureWidget.width;
        float widgetH = StableStructureWidget.height; // <-- use your widget height constant

        List<SectorEntityToken> objs = system.getEntitiesWithTag(Tags.OBJECTIVE);
        List<SectorEntityToken> obj2s = system.getEntitiesWithTag(Tags.STABLE_LOCATION);
        int total = objs.size();
        int total2 = obj2s.size();

        int perRow = (int) Math.floor((availableWidth + separator) / (widgetW + separator));
        perRow = Math.max(1, perRow);

        int rowsPlaced = (total + perRow - 1) / perRow;

        // Place
        for (int i = 0; i < total; i++) {
            int row = i / perRow;
            int col = i % perRow;

            int rowStartIndex = row * perRow;
            int remaining = total - rowStartIndex;
            int itemsThisRow = Math.min(perRow, remaining);

            float rowW = itemsThisRow * widgetW + (itemsThisRow - 1) * separator;
            float startX = Math.round((availableWidth - rowW) * 0.5f); // centered

            float x = startX + col * (widgetW + separator);
            float y = row * (widgetH + rowGap);

            SectorEntityToken token = objs.get(i);
            StableStructureWidget widget = new StableStructureWidget(token);

            tooltip.addCustom(widget.getMainPanel(), 0f).getPosition().inTL(x, y);
        }

        float neededHeight = rowsPlaced * widgetH + Math.max(0, rowsPlaced - 1) * rowGap;
        tooltip.setHeightSoFar(neededHeight);
        tooltipBottom.setParaFont(Fonts.INSIGNIA_LARGE);
        int totalAtAllPos = total2 + total;
        LabelAPI label =
                tooltipBottom.addPara(
                        "System Structures: %s / %s",
                        0f, Misc.getGrayColor(), Color.ORANGE, "" + total, totalAtAllPos + "");
        label.getPosition()
                .inTL(
                        contentPanel.getPosition().getWidth()
                                - label.computeTextWidth(label.getText())
                                - 5,
                        1);
        float x =
                contentPanel.getPosition().getWidth() - label.computeTextWidth(label.getText()) - 5;
        ButtonAPI bt =
                tooltipBottom.addButton(
                        "Manage Star System",
                        null,
                        faction.getBaseUIColor(),
                        faction.getDarkUIColor(),
                        Alignment.MID,
                        CutStyle.TL_BR,
                        Math.min(x - 20, 300),
                        25,
                        0f);
        bt.setShortcut(Keyboard.KEY_A, true);
        bt.setEnabled(false);
        tooltipBottom.addTooltipToPrevious(
                new TooltipMakerAPI.TooltipCreator() {
                    @Override
                    public boolean isTooltipExpandable(Object tooltipParam) {
                        return false;
                    }

                    @Override
                    public float getTooltipWidth(Object tooltipParam) {
                        return 400;
                    }

                    @Override
                    public void createTooltip(
                            TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                        tooltip.addPara(
                                "In wait of Stable Structure Rework coming in Theory of Toolbox 1.1",
                                3f);
                    }
                },
                TooltipMakerAPI.TooltipLocation.RIGHT,
                false);
        bt.getPosition().inTL(5, 0);

        contentPanel.addUIElement(tooltip).inTL(0, 0f);
        contentPanel
                .addUIElement(tooltipBottom)
                .inTL(0, contentPanel.getPosition().getHeight() - 20);
        mainPanel.addComponent(contentPanel).inTL(0f, 0f);
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
