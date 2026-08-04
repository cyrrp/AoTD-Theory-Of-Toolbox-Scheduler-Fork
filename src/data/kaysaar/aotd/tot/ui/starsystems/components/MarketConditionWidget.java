package data.kaysaar.aotd.tot.ui.starsystems.components;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import java.util.Comparator;
import java.util.List;

public class MarketConditionWidget implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel, contentPanel;
    MarketAPI market;

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    public MarketConditionWidget(float width, float height, MarketAPI market) {
        this.market = market;
        mainPanel = Global.getSettings().createCustom(width, height, this);
        createUI();
    }

    @Override
    public void createUI() {
        if (contentPanel != null) {
            contentPanel.removeComponent(mainPanel);
        }
        contentPanel =
                Global.getSettings()
                        .createCustom(
                                mainPanel.getPosition().getWidth(),
                                mainPanel.getPosition().getHeight(),
                                null);
        TooltipMakerAPI tooltip =
                contentPanel.createUIElement(
                        contentPanel.getPosition().getWidth(),
                        contentPanel.getPosition().getHeight(),
                        false);
        float seperator = 3f;
        List<MarketConditionAPI> marketConditions =
                market.getConditions().stream()
                        .filter(MarketConditionAPI::isPlanetary)
                        .sorted(
                                new Comparator<MarketConditionAPI>() {
                                    @Override
                                    public int compare(
                                            MarketConditionAPI o1, MarketConditionAPI o2) {
                                        return Float.compare(
                                                o2.getSpec().getOrder(), o1.getSpec().getOrder());
                                    }
                                })
                        .toList();

        float defaultWidth = 24;
        float defaultHeight = 24;
        float width = contentPanel.getPosition().getWidth();
        float separator = 3f;
        float iconWidthTotal = 0f;
        for (MarketConditionAPI marketCondition : marketConditions) {
            SpriteAPI sprite = Global.getSettings().getSprite(marketCondition.getSpec().getIcon());
            float ratio = sprite.getWidth() / sprite.getHeight();
            iconWidthTotal += defaultWidth * ratio;
        }
        float separatorTotal = separator * Math.max(0, marketConditions.size() - 1);
        float availableIconWidth = width - separatorTotal;
        if (iconWidthTotal > availableIconWidth && availableIconWidth > 0f) {
            float scale = availableIconWidth / iconWidthTotal;
            defaultWidth *= scale;
            defaultHeight *= scale;
        }
        float startingX = 0;
        for (MarketConditionAPI marketCondition : marketConditions) {
            SpriteAPI sprite = Global.getSettings().getSprite(marketCondition.getSpec().getIcon());
            float ratio = sprite.getWidth() / sprite.getHeight();
            ButtonWithImageComponent panel =
                    new ButtonWithImageComponent(
                            defaultWidth * ratio,
                            defaultHeight,
                            marketCondition.getSpec().getIcon());
            panel.setClickable(false);
            panel.setOverrideHighlight(true);
            tooltip.addCustom(panel.componentPanel, 0f).getPosition().inTL(startingX, 0);
            tooltip.addTooltipToPrevious(
                    new TooltipMakerAPI.TooltipCreator() {
                        @Override
                        public boolean isTooltipExpandable(Object tooltipParam) {
                            return true;
                        }

                        @Override
                        public float getTooltipWidth(Object tooltipParam) {
                            return 500;
                        }

                        @Override
                        public void createTooltip(
                                TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                            marketCondition.getPlugin().createTooltip(tooltip, expanded);
                            tooltip.setCodexEntryId(
                                    CodexDataV2.getConditionEntryId(
                                            marketCondition.getSpec().getId()));
                        }
                    },
                    TooltipMakerAPI.TooltipLocation.BELOW,
                    false);

            startingX += defaultWidth * ratio + seperator;
        }
        contentPanel.addUIElement(tooltip).inTL(0, 0);
        mainPanel.addComponent(contentPanel).inTL(0, 0);
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
