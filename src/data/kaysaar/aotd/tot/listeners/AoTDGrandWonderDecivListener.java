package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderAPI;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderManager;

public class AoTDGrandWonderDecivListener implements ColonyDecivListener {
    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {
        int amWonders = 0;
        for (Industry industry : market.getIndustries()) {
            if(industry instanceof GrandWonderAPI){
                amWonders++;
                GrandWonderManager.getInstance().removeBuiltSoFar(industry.getId());
            }
        }
        if (amWonders == 0) {
            return;
        }

        float penalty = -amWonders * 3f;
        for (MarketAPI factionMarket : Misc.getFactionMarkets(market.getFaction())) {
            if (factionMarket == market) {
                continue;
            }
            factionMarket.getStability().addTemporaryModFlat(
                    365f,
                    "aotd_wonder_loss",
                    "Loss of Grand Wonders",
                    penalty);
        }
    }

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {

    }
}
