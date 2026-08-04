package data.kaysaar.aotd.tot.ui.commodityDetailedInfo;

import ashlib.data.plugins.ui.models.BasePopUpDialog;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class CommodityDetailDialog extends BasePopUpDialog {
    MarketAPI market;
    String commodityId;
    FactionAPI factionAPI;
    AoTDDetailedCommodityPanelContent content;

    public CommodityDetailDialog(MarketAPI market, String commodityId) {
        super(null);
        this.market = market;
        this.commodityId = commodityId;
    }

    public CommodityDetailDialog(FactionAPI market, String commodityId) {
        super(null);
        this.factionAPI = market;
        this.commodityId = commodityId;
    }

    @Override
    public void createUI(CustomPanelAPI panelAPI) {
        createHeaader(panelAPI);

        TooltipMakerAPI tooltip =
                panelAPI.createUIElement(
                        panelAPI.getPosition().getWidth() - 30,
                        panelAPI.getPosition().getHeight() - y,
                        true);
        createContentForDialog(tooltip, panelAPI.getPosition().getWidth() - 30);
        addTooltip(tooltip);
        panelAPI.addUIElement(tooltip).inTL(x, y);
    }

    @Override
    public void createContentForDialog(TooltipMakerAPI tooltip, float width) {
        super.createContentForDialog(tooltip, width);
        if (market != null) {
            content = new AoTDDetailedCommodityPanelContent(width, 650, market, commodityId);
        } else {
            content = new AoTDDetailedCommodityPanelContent(width, 650, factionAPI, commodityId);
        }

        tooltip.addCustom(content.getMainPanel(), 1f);
    }

    @Override
    public void onExit() {
        super.onExit();
        content.clearUI();
    }
}
