package data.kaysaar.aotd.tot.scripts.submarket.nex;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDStockpileCalculator;
import exerelin.campaign.submarkets.Nex_MilitarySubmarketPlugin;

public class AoTDxNexMilitarySubmarketPlugin extends Nex_MilitarySubmarketPlugin {
    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        return AoTDStockpileCalculator.getLegalMarketStockpileLimit(
                com, market, submarket.getSpecId());
    }
}
