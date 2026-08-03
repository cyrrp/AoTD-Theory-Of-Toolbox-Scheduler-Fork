package data.kaysaar.aotd.tot.scripts.submarket.nex;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDStockpileCalculator;
import exerelin.campaign.submarkets.Nex_BlackMarketPlugin;

public class AoTDxNexBlackMarketPlugin extends Nex_BlackMarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getBlackMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }
}
