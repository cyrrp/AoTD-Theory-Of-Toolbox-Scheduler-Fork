package data.kaysaar.aotd.tot.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.Farming;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Pair;

public class AoTDFarming extends Farming {
    // Note : Will need listener after new patch instead
    public void apply() {
        super.apply(true);

        int size = market.getSize();
        boolean aquaculture = Industries.AQUACULTURE.equals(getId());

        if (aquaculture) {
            demand(0, Commodities.HEAVY_MACHINERY, size, BASE_VALUE_TEXT);
        } else {
            demand(0, Commodities.HEAVY_MACHINERY, size - 3, BASE_VALUE_TEXT);
        }

        // supply(3, Commodities.LOBSTER, 5, "Hack");

        // ResourceDepositsCondition sets base value
        // makes more sense for Mining where mining doesn't have to check for existence of resource
        // conditions

        //		int deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        //		supply(1, Commodities.FOOD, -deficit, getDeficitText(Commodities.HEAVY_MACHINERY));
        //		supply(1, Commodities.ORGANICS, -deficit, getDeficitText(Commodities.HEAVY_MACHINERY));

        Pair<String, Integer> deficit = getMaxDeficit(Commodities.HEAVY_MACHINERY);
        if (deficit.two > 0) {
            float percentage =
                    (float) deficit.two
                            / this.getDemand(Commodities.HEAVY_MACHINERY)
                                    .getQuantity()
                                    .getModifiedInt();
            int reduction =
                    Math.round(
                            this.getSupply(Commodities.FOOD).getQuantity().getModifiedInt()
                                    * percentage);
            // applyDeficitToProduction(0, deficit, Commodities.FOOD, Commodities.ORGANICS);
            deficit.two = reduction;
            applyDeficitToProduction(0, deficit, Commodities.FOOD);
        }
        if (!isFunctional()) {
            supply.clear();
        }
    }
}
