package data.kaysaar.aotd.tot.ui.core.onhover;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.campaign.econ.Market;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import java.awt.*;
import java.util.Comparator;
import java.util.List;

public class IncomePanelOnHover implements TooltipMakerAPI.TooltipCreator {
    MarketAPI marketAPI;

    public IncomePanelOnHover(MarketAPI marketAPI) {
        this.marketAPI = marketAPI;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return true;
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 550;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.addTitle("Monthly Income & Upkeep", marketAPI.getFaction().getBaseUIColor());
        tooltip.addPara(
                "The net income for this colony is expected to be around %s",
                5f,
                Color.ORANGE,
                Misc.getDGSCredits(
                        AoTDToolboxMisc.getExpectedMonthlyNetIncomeFromMarket(marketAPI)));
        tooltip.addPara(
                "Income multiplier: %s",
                5f,
                Color.ORANGE,
                Math.round(marketAPI.getIncomeMult().getModifiedValue() * 100) + "%");
        tooltip.addStatModGrid(
                getTooltipWidth(tooltipParam),
                50.0F,
                10f,
                3f,
                marketAPI.getIncomeMult(),
                true,
                null);
        tooltip.addPara(
                "*Income multiplier does only apply to income of market's industries!",
                Misc.getGrayColor(),
                5f);
        tooltip.addPara(
                "Upkeep multiplier: %s",
                10f,
                Color.ORANGE,
                Math.round(marketAPI.getUpkeepMult().getModifiedValue() * 100) + "%");
        tooltip.addStatModGrid(
                getTooltipWidth(tooltipParam),
                50.0F,
                10f,
                3f,
                marketAPI.getUpkeepMult(),
                true,
                null);
        float totalIncomeFromLocal = 0;
        float totalIncomeFromLocalTrade = 0;
        float totalIncomeFromExpectedTrade = 0;
        float totalIncomeFromContracts = 0;
        float upkeep = 0f;
        AoTDEconomy.pruneCommoditiesThatMightAppear((Market) marketAPI);
        List<Industry> industryList =
                marketAPI.getIndustries().stream()
                        .sorted(
                                new Comparator<Industry>() {
                                    @Override
                                    public int compare(Industry o1, Industry o2) {
                                        return o2.getIncome().getModifiedInt()
                                                - o1.getIncome().getModifiedInt();
                                    }
                                })
                        .toList();
        List<CommodityOnMarketAPI> coms =
                marketAPI.getCommoditiesCopy().stream()
                        .sorted(
                                new Comparator<CommodityOnMarketAPI>() {
                                    @Override
                                    public int compare(
                                            CommodityOnMarketAPI o1, CommodityOnMarketAPI o2) {
                                        return AoTDToolboxMisc
                                                        .getExpectedMonthlyIncomeFromCommodity(
                                                                (AoTDCommodityOnMarket) o2)
                                                - AoTDToolboxMisc
                                                        .getExpectedMonthlyIncomeFromCommodity(
                                                                (AoTDCommodityOnMarket) o1);
                                    }
                                })
                        .toList();
        for (Industry industry : industryList) {
            totalIncomeFromLocal += industry.getIncome().getModifiedInt();
            upkeep += industry.getUpkeep().getModifiedInt();
        }
        for (CommodityOnMarketAPI com : coms) {
            totalIncomeFromLocalTrade +=
                    AoTDToolboxMisc.getGuaranteedExportIncome((AoTDCommodityOnMarket) com);
            totalIncomeFromExpectedTrade +=
                    AoTDToolboxMisc.getSpeculatedExportIncome((AoTDCommodityOnMarket) com);
            totalIncomeFromContracts +=
                    AoTDToolboxMisc.getSpeculatedExportIncomeFromContracts(
                            (AoTDCommodityOnMarket) com);
        }
        if (totalIncomeFromLocal > 0 || totalIncomeFromLocalTrade > 0) {
            tooltip.addSectionHeading(
                    "Guaranteed Income",
                    marketAPI.getFaction().getBaseUIColor(),
                    marketAPI.getFaction().getDarkUIColor(),
                    Alignment.MID,
                    5f);
            tooltip.addPara(
                    "Income from local industries: %s",
                    5f, Color.ORANGE, Misc.getDGSCredits(totalIncomeFromLocal));
            tooltip.addPara(
                    "Income from in-faction trade: %s",
                    3f, Color.ORANGE, Misc.getDGSCredits(totalIncomeFromLocalTrade));
        }
        if (totalIncomeFromExpectedTrade > 0 || totalIncomeFromContracts > 0) {
            tooltip.addSectionHeading(
                    "Speculated Income",
                    marketAPI.getFaction().getBaseUIColor(),
                    marketAPI.getFaction().getDarkUIColor(),
                    Alignment.MID,
                    5f);
            tooltip.addPara(
                    "Income from exports to external markets: %s",
                    5f, Color.ORANGE, Misc.getDGSCredits(totalIncomeFromExpectedTrade));
            tooltip.addPara(
                    "Income from trade contracts: %s",
                    3f, Color.ORANGE, Misc.getDGSCredits(totalIncomeFromContracts));
            tooltip.addPara(
                    "*Projected income is speculative due to the unpredictable nature of interstellar trade. If the global market is experiencing a surplus, this market may lack viable export opportunities.",
                    Misc.getGrayColor(),
                    5f);
        }
        tooltip.addPara(
                "*Internal exports are prioritized by market accessibility. "
                        + "Trade flows first from the most accessible worlds to the least. "
                        + "Changes in accessibility can shift export priority, which may lower income in some markets while increasing it in others.",
                Misc.getGrayColor(),
                10f);

        tooltip.addSectionHeading(
                "Upkeep",
                marketAPI.getFaction().getBaseUIColor(),
                marketAPI.getFaction().getDarkUIColor(),
                Alignment.MID,
                10f);
        tooltip.addPara(
                "From Local Industries: %s",
                5f, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(upkeep));
        int var30 = (int) marketAPI.getShortageCounteringCost();
        if (var30 > 0 || marketAPI.isUseStockpilesForShortages()) {
            String var31 = Misc.getDGSCredits((float) var30);
            tooltip.addPara(
                    "Shortage countering: %s",
                    3f, Misc.getNegativeHighlightColor(), new String[] {var31});
        }
        int var32 = (int) marketAPI.getImmigrationIncentivesCost();
        if (var32 > 0 && marketAPI.isImmigrationIncentivesOn()) {
            String var33 = Misc.getDGSCredits((float) var32);
            tooltip.addPara(
                    "Hazard pay: %s", 3f, Misc.getNegativeHighlightColor(), new String[] {var33});
        }
    }
}
