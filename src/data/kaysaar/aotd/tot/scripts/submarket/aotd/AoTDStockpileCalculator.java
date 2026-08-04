package data.kaysaar.aotd.tot.scripts.submarket.aotd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import java.util.Random;

/** Shared stockpile calculation for vanilla and compatibility submarkets. */
public final class AoTDStockpileCalculator {
    private static final float MIN_MONTHLY_VARIATION = 0.9f;
    private static final float MONTHLY_VARIATION_RANGE = 0.2f;
    private static final float MIN_STABILITY_MULTIPLIER = 0.25f;
    private static final float STABILITY_MULTIPLIER_RANGE = 0.75f;

    private AoTDStockpileCalculator() {}

    /**
     * Returns the unmodified AoTD stockpile limit. Availability is governed by the current
     * remaining deficit, not by the monthly deficit anchor.
     */
    static int getBaseStockpileLimit(CommodityOnMarketAPI com) {
        if (!(com instanceof AoTDCommodityOnMarket commodity)) {
            return (int) OpenMarketPlugin.getBaseStockpileLimit(com);
        }

        if (commodity.getDeficitQuantity() > 0) {
            return 0;
        }

        commodity.getSupplyDemandData().updateSupplyDemandData(commodity.getMarket());

        float supply = commodity.getSupplyDemandData().getTotalRawUnitsFromSupply();
        float demand = commodity.getSupplyDemandData().getTotalRawUnitsFromDemand();
        float imports = demand - supply;
        float limit = 0f;

        if (imports > 0 && commodity.getDef() <= 0) {
            limit += imports * 0.05f;
        }
        limit += supply * 0.25f;
        limit -= commodity.getDeficitQuantity();
        limit += commodity.getExcessQuantity();

        return (int) Math.max(0f, limit);
    }

    public static int getLegalMarketStockpileLimit(
            CommodityOnMarketAPI com, MarketAPI market, String submarketSpecId) {
        return applyAvailabilityModifiers(
                getBaseStockpileLimit(com),
                getMonthlyVariation(market, submarketSpecId),
                getLegalStabilityMultiplier(market.getStabilityValue()));
    }

    public static int getBlackMarketStockpileLimit(
            CommodityOnMarketAPI com, MarketAPI market, String submarketSpecId) {
        return applyAvailabilityModifiers(
                getBaseStockpileLimit(com),
                getMonthlyVariation(market, submarketSpecId),
                getBlackMarketStabilityMultiplier(market.getStabilityValue()));
    }

    static float getLegalStabilityMultiplier(float stability) {
        return MIN_STABILITY_MULTIPLIER + STABILITY_MULTIPLIER_RANGE * (stability / 10f);
    }

    static float getBlackMarketStabilityMultiplier(float stability) {
        return MIN_STABILITY_MULTIPLIER + STABILITY_MULTIPLIER_RANGE * (1f - stability / 10f);
    }

    static int applyAvailabilityModifiers(
            float baseLimit, float monthlyVariation, float stabilityMultiplier) {
        float limit = baseLimit * monthlyVariation * stabilityMultiplier;
        return (int) Math.max(0f, limit);
    }

    private static float getMonthlyVariation(MarketAPI market, String submarketSpecId) {
        Random random =
                new Random(
                        market.getId().hashCode()
                                + submarketSpecId.hashCode()
                                + Global.getSector().getClock().getMonth() * 170000);
        return MIN_MONTHLY_VARIATION + MONTHLY_VARIATION_RANGE * random.nextFloat();
    }
}
