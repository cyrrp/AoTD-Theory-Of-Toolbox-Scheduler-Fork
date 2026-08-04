package data.kaysaar.aotd.tot.scripts.submarket.hmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import data.campaign.submarkets.HMI_ScrapMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import java.util.Random;

public class AoTDHMI_ScrapMarketPlugin extends HMI_ScrapMarketPlugin {
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        float limit = AoTDOpenMarketPlugin.getBaseStockpileLimit(com);
        Random random =
                new Random(
                        (long)
                                (this.market.getId().hashCode()
                                        + this.submarket.getSpecId().hashCode()
                                        + Global.getSector().getClock().getMonth() * 170000));
        limit *= 0.9F + 0.2F * random.nextFloat();
        float sm = this.market.getStabilityValue() / 10.0F;
        limit *= 0.25F + 0.75F * sm;
        if (!com.getCommodity().getId().equals("metals")
                && !com.getCommodity().getId().equals("fuel")
                && !com.getCommodity().getId().equals("heavy_machinery")) {
            limit *= 0.0F;
        } else {
            limit *= 0.25F;
        }

        if (limit < 0.0F) {
            limit = 0.0F;
        }

        return (int) limit;
    }
}
