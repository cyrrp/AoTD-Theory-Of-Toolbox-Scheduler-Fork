package data.kaysaar.aotd.tot.ui.industry;

import ashlib.data.plugins.ui.models.ExtendedUIPanelPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.kaysaar.aotd.tot.plugins.ReflectionUtilis;
import data.kaysaar.aotd.tot.ui.commoditypanel.AoTDCommodityShortPanelCombined;
import java.util.List;

public class IndustryOnHoverTooltipV2 implements ExtendedUIPanelPlugin {
    CustomPanelAPI mainPanel;
    Industry ind;
    boolean expanded;
    boolean ignoreDeficits = false;
    Industry.IndustryTooltipMode mode = Industry.IndustryTooltipMode.NORMAL;
    TooltipMakerAPI tl;

    public TooltipMakerAPI getTl() {
        return tl;
    }

    public IndustryOnHoverTooltipV2(float width, Industry ind, boolean expanded) {
        mainPanel = Global.getSettings().createCustom(width, 1, this);
        this.ind = ind;
        this.expanded = expanded;
        createUI();
    }

    public IndustryOnHoverTooltipV2(
            float width, Industry ind, boolean expanded, Industry.IndustryTooltipMode mode) {
        mainPanel = Global.getSettings().createCustom(width, 1, this);
        this.ind = ind;
        this.expanded = expanded;
        this.mode = mode;
        if (mode == Industry.IndustryTooltipMode.ADD_INDUSTRY
                || mode == Industry.IndustryTooltipMode.UPGRADE) {
            ignoreDeficits = true;
        }
        createUI();
    }

    public IndustryOnHoverTooltipV2(
            float width, Industry ind, boolean expanded, boolean ignoreDeficits) {
        mainPanel = Global.getSettings().createCustom(width, 1, this);
        this.ind = ind;
        this.expanded = expanded;
        this.ignoreDeficits = ignoreDeficits;
        createUI();
    }

    @Override
    public CustomPanelAPI getMainPanel() {
        return mainPanel;
    }

    @Override
    public void createUI() {
        TooltipMakerAPI tooltipHeight =
                mainPanel.createUIElement(mainPanel.getPosition().getWidth(), 10000, false);
        TooltipMakerAPI firstHalf =
                mainPanel.createUIElement(mainPanel.getPosition().getWidth(), 100000, true);
        // The economy pipeline owns materialized market state. Tooltips are
        // read-only consumers and must not replay conditions, apply industries,
        // or temporarily overwrite commodity availability on the live market.
        ind.createTooltip(mode, firstHalf, expanded);
        UIPanelAPI holder = (UIPanelAPI) ReflectionUtilis.getChildrenCopy(firstHalf).get(0);
        List<UIComponentAPI> comps = ReflectionUtilis.getChildrenCopy((UIPanelAPI) holder);
        int recordedPositionProducitonOnList, recordedPostitionDemandOnList;
        recordedPostitionDemandOnList = -3;
        recordedPositionProducitonOnList = -3;
        for (int i = 0; i < comps.size(); i++) {
            UIComponentAPI comp = comps.get(i);
            if (comp instanceof LabelAPI label) {
                String text = label.getText();
                if ("Production".equals(text)) {
                    // remove the label
                    recordedPositionProducitonOnList = i + 1;
                    //                    comps.remove(i);
                    //                    comps.remove(i);
                    //                    comps.remove(i);
                    //                    i--; // keep loop stable after removals
                }
                if ("Demand & effects".equals(text)) {
                    recordedPostitionDemandOnList = i + 1;
                    //                    comps.remove(i);
                    //                    comps.remove(i);
                    //                    comps.remove(i);
                    //                    i--; // keep loop stable after removals
                }
            }
        }
        float lastRecordedY = 0;
        float additionalHeightToCover = 0;
        float additionalHeightToCoverDemand = 0;
        for (int j = 0; j < comps.size(); ) {
            UIComponentAPI comp = comps.get(j);
            float y = comp.getPosition().getY();
            if (j == recordedPositionProducitonOnList) {
                AoTDCommodityShortPanelCombined production =
                        new AoTDCommodityShortPanelCombined(
                                mainPanel.getPosition().getWidth(), 3, ind, false, false);
                tooltipHeight.addCustom(production.getMainPanel(), 5f);
                float positionInUIHeader = -comps.get(j - 1).getPosition().getY();
                float positionInUIBottom = -comps.get(j + 1).getPosition().getY();
                float available = positionInUIBottom - positionInUIHeader;
                float left = available - production.getMainPanel().getPosition().getHeight() - 5f;
                additionalHeightToCover = -left;
                j += 2;
            } else if (j == recordedPostitionDemandOnList) {
                AoTDCommodityShortPanelCombined production =
                        new AoTDCommodityShortPanelCombined(
                                mainPanel.getPosition().getWidth(), 3, ind, true, ignoreDeficits);
                tooltipHeight.addCustom(production.getMainPanel(), 5f);
                float positionInUIHeader = -comps.get(j - 1).getPosition().getY();
                float positionInUIBottom = -comps.get(j + 1).getPosition().getY();
                float available = positionInUIBottom - positionInUIHeader;
                float left = available - production.getMainPanel().getPosition().getHeight() - 5f;
                additionalHeightToCover = 0;
                additionalHeightToCoverDemand = -left;
                j += 2;

            } else {
                float curY = Math.abs(y) + additionalHeightToCover + additionalHeightToCoverDemand;
                tooltipHeight
                        .addCustom(comp, 0f)
                        .getPosition()
                        .inTL(0, curY - (comp.getPosition().getHeight()));
                if (curY >= lastRecordedY) {
                    lastRecordedY = curY;
                }
                ;
                j++;
            }
        }

        mainPanel.getPosition().setSize(mainPanel.getPosition().getWidth(), lastRecordedY);
        mainPanel.addUIElement(tooltipHeight).inTL(0, 0);
        this.tl = tooltipHeight;
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
