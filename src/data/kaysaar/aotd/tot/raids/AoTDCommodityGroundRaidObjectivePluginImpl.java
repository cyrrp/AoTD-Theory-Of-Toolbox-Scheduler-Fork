package data.kaysaar.aotd.tot.raids;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ShippingDisruption;
import com.fs.starfarer.api.impl.campaign.graid.CommodityGroundRaidObjectivePluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.IconGroupAPI;
import com.fs.starfarer.api.ui.IconRenderMode;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import java.awt.*;
import java.util.Random;

public class AoTDCommodityGroundRaidObjectivePluginImpl
        extends CommodityGroundRaidObjectivePluginImpl {
    public AoTDCommodityGroundRaidObjectivePluginImpl(MarketAPI market, String commodityId) {
        super(market, commodityId);
        com = market.getCommodityData(commodityId);
        setSource(computeCommoditySourceAoTD(market, com));
    }

    private int deficitActuallyCaused;

    public void addIcons(IconGroupAPI iconGroup) {
        if (com instanceof AoTDCommodityOnMarket commodityOnMarket) {
            if (com.getId().equals(Commodities.FOOD)
                    || com.getId().equals(Commodities.DOMESTIC_GOODS)) {
                int total =
                        AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                                .getCalculationScript()
                                .convertRawUnitsToDemand(
                                        commodityOnMarket
                                                .getSupplyDemandData()
                                                .getTotalRawUnitsFromDemand(),
                                        com.getMarket(),
                                        com.getId());
                if (com.getId().equals(Commodities.FOOD)) {
                    total =
                            Math.toIntExact(
                                    Math.round(total / Math.pow(2, com.getMarket().getSize() - 1)));
                } else {
                    total =
                            Math.toIntExact(
                                    Math.round(total / Math.pow(2, com.getMarket().getSize() - 3)));
                }
                iconGroup.addIconGroup(id, IconRenderMode.NORMAL, total, null);

            } else {
                iconGroup.addIconGroup(
                        id,
                        IconRenderMode.NORMAL,
                        AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                                .getCalculationScript()
                                .convertRawUnitsToDemand(
                                        commodityOnMarket
                                                .getSupplyDemandData()
                                                .getTotalRawUnitsFromDemand(),
                                        market,
                                        commodityOnMarket.getSpec().getId()),
                        null);
            }
        } else {
            super.addIcons(iconGroup);
        }
    }

    public static Industry computeCommoditySourceAoTD(MarketAPI market, CommodityOnMarketAPI com) {
        if (com instanceof AoTDCommodityOnMarket commodityOnMarket) {
            Industry best = null;
            int score = 0;
            MarketCMD.RaidDangerLevel base = com.getCommodity().getBaseDanger();
            for (Industry ind : market.getIndustries()) {
                int supply = commodityOnMarket.getSupplyDemandData().getRawSupplyFromIndustry(ind);
                int deficit = ind.getMaxDeficit(commodityOnMarket.getSpec().getId()).two;
                int units =
                        AoTDCommodityEconSpecManager.getCargoAmountFromSupplyOrDemand(
                                deficit, true, commodityOnMarket.getSpec().getId());
                int demandMet =
                        commodityOnMarket.getSupplyDemandData().getRawDemandFromIndustry(ind)
                                - units;
                int currScore = Math.max(supply, demandMet) * 1000;
                MarketCMD.RaidDangerLevel danger =
                        ind.adjustCommodityDangerLevel(com.getId(), base);
                currScore += 1000 - danger.ordinal();
                if (currScore > score) {
                    score = currScore;
                    best = ind;
                }
            }
            return best;
        } else {
            return computeCommoditySource(market, com);
        }
    }

    public int performRaid(CargoAPI loot, Random random, float lootMult, TextPanelAPI text) {
        if (marinesAssigned <= 0) return 0;

        float base = getQuantity(marinesAssigned);
        base *= lootMult;

        float mult = 0.9f + random.nextFloat() * 0.2f;
        base *= mult;

        quantityLooted = (int) base;
        if (quantityLooted < 1) quantityLooted = 1;

        loot.addCommodity(getId(), quantityLooted);

        deficitActuallyCaused = getDeficitCausedForAval();
        if (deficitActuallyCaused > 0) {
            com.getAvailableStat()
                    .addTemporaryModFlat(
                            ShippingDisruption.ACCESS_LOSS_DURATION,
                            Misc.genUID(),
                            "Recent raid",
                            -deficitActuallyCaused);
        }

        xpGained = (int) (quantityLooted * getCommoditySpec().getBasePrice() * XP_GAIN_VALUE_MULT);
        return xpGained;
    }

    public int getDeficitCausedForAval() {
        float quantity = getQuantity(getMarinesAssigned(), true);
        return AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                .getCalculationScript()
                .convertRawUnitsToDemand(quantity, market, com.getId());
    }

    public int getDeficitCaused() {
        float quantity = getQuantity(getMarinesAssigned(), true);
        if (com.getId().equals(Commodities.FOOD)) {
            int total =
                    AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                            .getCalculationScript()
                            .convertRawUnitsToDemand(quantity, market, com.getId());
            total = Math.toIntExact(Math.round(total / Math.pow(2, com.getMarket().getSize() - 1)));
            return total;
        }
        if (com.getId().equals(Commodities.DOMESTIC_GOODS)) {
            int total =
                    AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                            .getCalculationScript()
                            .convertRawUnitsToDemand(quantity, market, com.getId());
            total = Math.toIntExact(Math.round(total / Math.pow(2, com.getMarket().getSize() - 3)));
            return total;
        }
        return AoTDCommodityEconSpecManager.getEconSpec(com.getId())
                .getCalculationScript()
                .convertRawUnitsToDemand(quantity, market, com.getId());
    }

    public float getBaseRaidQuantity(boolean forDeficit) {
        // CommodityOnMarketAPI com = market.getCommodityData(id);
        if (com instanceof AoTDCommodityOnMarket commodity) {
            if (commodity.getSupplyDemandData().getTotalRawUnitsFromDemand() <= 0) return 0;

            float result = 0f;

            if (forDeficit) {
                result +=
                        commodity.getSupplyDemandData().getTotalRawUnitsFromDemand()
                                * QUANTITY_MULT_NORMAL_FOR_DEFICIT;
            } else {
                result +=
                        commodity.getSupplyDemandData().getTotalRawUnitsFromDemand()
                                * QUANTITY_MULT_NORMAL;
            }
            result += commodity.getExcessQuantity();
            result -= commodity.getDeficitQuantity();

            if (result < 0) result = 0;

            return result * QUANTITY_MULT_OVERALL;
        }
        return 0;
    }
}
