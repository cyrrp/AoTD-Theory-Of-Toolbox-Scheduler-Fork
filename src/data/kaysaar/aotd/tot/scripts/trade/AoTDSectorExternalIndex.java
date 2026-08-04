// file: data/kaysaar/aotd/tot/scripts/trade/AoTDSectorExternalIndex.java
package data.kaysaar.aotd.tot.scripts.trade;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AoTDSectorExternalIndex {

    public static final class Offer {
        /** null for scavenger/synthetic offers */
        public final MarketAPI market;

        /** never null; for scavenger this is a dummy holder with remainingNet map */
        public final AoTDMarketData data;

        /** positive amount available/needed */
        public int amount;

        /** WeightedRandomPicker weight */
        public float weight;

        /** true if this offer is synthetic (no market) */
        public boolean isScavenger;

        public Offer(MarketAPI market, AoTDMarketData data, int amount, float weight) {
            this.market = market;
            this.data = data;
            this.amount = amount;
            this.weight = weight;
        }

        /** Convenience guard for apply-to-market phases. */
        public boolean hasMarket() {
            return market != null && !isScavenger;
        }
    }

    public final Map<String, ArrayList<Offer>> exportersByCommodity = new HashMap<>();
    public final Map<String, ArrayList<Offer>> importersByCommodity = new HashMap<>();

    public void addMarket(MarketAPI market, AoTDMarketData md) {
        for (Map.Entry<String, Integer> e : md.remainingNet.entrySet()) {
            String commodityId = e.getKey();
            int net = e.getValue();
            if (net == 0) continue;
            CommodityOnMarketAPI commodity = market.getCommodityData(commodityId);
            if (net > 0) {
                float bonus = 0;
                int supply = net;
                float w = computeOfferWeight(md.outsideWeight, bonus, false);
                exportersByCommodity
                        .computeIfAbsent(commodityId, k -> new ArrayList<>())
                        .add(new Offer(market, md, supply, w));
            } else {
                float bonus = 0;
                int need = -net;
                if (commodity instanceof AoTDCommodityOnMarket commodityOnMarket) {
                    int deficit = commodityOnMarket.getDeficitQuantity();
                    int totalDemand =
                            commodityOnMarket.getSupplyDemandData().getTotalRawUnitsFromDemand();
                    int mult =
                            Math.max(
                                    commodityOnMarket.getExcDefData().getDeficitConsequtiveMonths(),
                                    1);
                    if (deficit > 0) {
                        bonus = ((float) (deficit * 1.5f) / totalDemand) * mult;
                    }
                }
                float w = computeOfferWeight(md.outsideWeight, bonus, true);
                importersByCommodity
                        .computeIfAbsent(commodityId, k -> new ArrayList<>())
                        .add(new Offer(market, md, need, w));
            }
        }
    }

    /** Create synthetic scavenger exporter offer. */
    public Offer createScavengerOffer(String commodityId, int amount, float weight) {
        AoTDMarketData dummy = AoTDMarketData.createScavengerDummy();
        dummy.remainingNet.put(commodityId, amount);

        Offer o = new Offer(null, dummy, amount, weight);
        o.isScavenger = true;
        return o;
    }

    /**
     * Base = outsideWeight (accessibility). Tiny bonus for bigger deficit/excess (caps at +40%).
     */
    public static float computeOfferWeight(float baseOutsideWeight, float amount, boolean deficit) {
        float base = Math.max(0.01f, baseOutsideWeight);
        float amountFactor = 1f;
        // up to +10% at 100k
        if (amount > 0) {
            amountFactor = amount;
        }

        return base * amountFactor;
    }
}
