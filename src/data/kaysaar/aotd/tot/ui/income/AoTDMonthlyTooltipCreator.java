package data.kaysaar.aotd.tot.ui.income;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.ui.commoditypanel.CommodityButtonOnHover;
import data.kaysaar.aotd.tot.ui.industry.IndustryOnHoverTooltipV2;

public class AoTDMonthlyTooltipCreator implements TooltipMakerAPI.TooltipCreator {
    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return true;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        MonthlyReport.FDNode node = (MonthlyReport.FDNode) tooltipParam;
        if (node.custom instanceof Industry ind) {
            return 400;
        }
        if (node.custom instanceof AoTDCommodityOnMarket commodity) {
            return 550;
        }
        return 450;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        MonthlyReport.FDNode node = (MonthlyReport.FDNode) tooltipParam;
        if (node.custom instanceof Industry ind) {
            IndustryOnHoverTooltipV2 tl =
                    new IndustryOnHoverTooltipV2(getTooltipWidth(tooltipParam), ind, expanded);
            tl.createUI();
            tooltip.addCustom(tl.getMainPanel(), 0f);
        }
        if (node.custom instanceof AoTDCommodityOnMarket commodity) {
            CommodityButtonOnHover hover =
                    new CommodityButtonOnHover(
                            commodity.getSpec(), commodity.getMarket(), true, true);
            hover.createTooltip(tooltip, expanded, tooltipParam);
        }
    }
}
