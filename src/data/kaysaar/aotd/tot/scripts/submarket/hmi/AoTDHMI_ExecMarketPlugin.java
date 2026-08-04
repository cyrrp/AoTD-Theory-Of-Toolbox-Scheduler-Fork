package data.kaysaar.aotd.tot.scripts.submarket.hmi;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import data.campaign.submarkets.HMI_ExecMarketPlugin;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import java.util.Random;

public class AoTDHMI_ExecMarketPlugin extends HMI_ExecMarketPlugin {
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        float limit = AoTDOpenMarketPlugin.getBaseStockpileLimit(com);
        Random random =
                new Random(
                        (long)
                                (this.market.getId().hashCode()
                                        + this.submarket.getSpecId().hashCode()
                                        + Global.getSector().getClock().getMonth() * 170000));
        limit *= 0.7F + 0.2F * random.nextFloat();
        float sm = this.market.getStabilityValue() / 10.0F;
        limit *= 0.25F + 0.75F * sm;
        if (!com.getCommodity().getId().equals("ore")
                && !com.getCommodity().getId().equals("rare_ore")
                && !com.getCommodity().getId().equals("organics")
                && !com.getCommodity().getId().equals("volatiles")
                && !com.getCommodity().getId().equals("organs")
                && !com.getCommodity().getId().equals("drugs")
                && !com.getCommodity().getId().equals("hand_weapons")
                && !com.getCommodity().getId().equals("marines")) {
            limit *= 0.25F;
        } else {
            limit *= 1.5F;
        }

        if (limit < 0.0F) {
            limit = 0.0F;
        }

        return (int) limit;
    }
}
