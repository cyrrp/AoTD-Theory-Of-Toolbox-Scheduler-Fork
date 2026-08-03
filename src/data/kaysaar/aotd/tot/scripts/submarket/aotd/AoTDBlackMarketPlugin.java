package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;

public class AoTDBlackMarketPlugin extends BlackMarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getBlackMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }
}
