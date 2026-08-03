package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;

public class AoTDMilitarySubmarketPlugin extends MilitarySubmarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getLegalMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }
}
