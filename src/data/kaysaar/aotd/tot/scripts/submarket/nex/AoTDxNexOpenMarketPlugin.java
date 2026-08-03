package data.kaysaar.aotd.tot.scripts.submarket.nex;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDStockpileCalculator;
import exerelin.campaign.submarkets.Nex_OpenMarketPlugin;

public class AoTDxNexOpenMarketPlugin extends Nex_OpenMarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getLegalMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }
}
