package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;

public class AoTDOpenMarketPlugin extends OpenMarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getLegalMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }

    /** Compatibility entry point used by the price table for the live open-market limit. */
    public static int getStockPileToolbox(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getLegalMarketStockpileLimit(
                com, com.getMarket(), Submarkets.SUBMARKET_OPEN);
    }
}
