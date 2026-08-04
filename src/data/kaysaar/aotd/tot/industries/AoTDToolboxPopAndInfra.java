package data.kaysaar.aotd.tot.industries;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.campaign.econ.Market;
import data.kaysaar.aotd.tot.grandwonders.GrandWonderManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.strings.AoTDMarketStats;
import java.awt.*;

public class AoTDToolboxPopAndInfra extends PopulationAndInfrastructure {
    @Override
    public void doPostSaveRestore() {
        super.doPostSaveRestore();
        AoTDEconomy.pruneCommoditiesThatMightAppear((Market) market);
    }

    public Pair<String, Integer> getDeficitAmountPenalty(String commodity) {
        Pair<String, Integer> result = new Pair<>();
        if (commodity.equals(Commodities.FOOD)) {
            int max = market.getSize();
            int toMet = getDemand(Commodities.FOOD).getQuantity().getModifiedInt();

            int missing = getMaxDeficit(commodity).two;
            float percentage = (float) Math.abs(missing) / toMet;
            result.one = commodity;
            result.two = Math.round(percentage * max);
            return result;
        }
        if (commodity.equals(Commodities.DOMESTIC_GOODS)) {
            int max = market.getSize() - 1;
            int toMet = getDemand(Commodities.DOMESTIC_GOODS).getQuantity().getModifiedInt();
            int missing = getMaxDeficit(commodity).two;
            float percentage = (float) missing / toMet;
            result.one = commodity;
            result.two = Math.round(percentage * max);
            return result;
        }

        return getMaxDeficit(commodity);
    }

    public void apply() {

        modifyStabilityToolbox(this, market, getModId(3));

        //		if (market.getId().equals("chicomoztoc")) {
        //		System.out.println("wefwefwe");
        //	}

        super.apply(true);

        int size = market.getSize();

        demand(Commodities.FOOD, size);
        getDemand(Commodities.FOOD)
                .getQuantity()
                .modifyMultAlways("aotd_new_scale", (float) Math.pow(2, market.getSize() - 1), "");

        if (!market.hasCondition(Conditions.HABITABLE)) {
            demand(Commodities.ORGANICS, size - 1);
        }

        int luxuryThreshold = 3;

        demand(Commodities.DOMESTIC_GOODS, size - 1);
        getDemand(Commodities.DOMESTIC_GOODS)
                .getQuantity()
                .modifyMultAlways("aotd_new_scale", (float) Math.pow(2, market.getSize() - 3), "");
        demand(Commodities.LUXURY_GOODS, size - luxuryThreshold);

        demand(Commodities.DRUGS, size - 2);
        demand(Commodities.ORGANS, size - 3);

        demand(Commodities.SUPPLIES, Math.min(size, 3));

        supply(Commodities.CREW, size - 3);
        supply(Commodities.DRUGS, size - 4);
        supply(Commodities.ORGANS, size - 5);

        Pair<String, Integer> deficit = getDeficitAmountPenalty(Commodities.DOMESTIC_GOODS);
        if (deficit.two <= 0) {
            market.getStability().modifyFlat(getModId(0), 1, "Domestic goods demand met");
        } else {
            market.getStability().unmodifyFlat(getModId(0));
        }

        deficit = getMaxDeficit(Commodities.LUXURY_GOODS);
        if (deficit.two <= 0 && size > luxuryThreshold) {
            market.getStability().modifyFlat(getModId(1), 1, "Luxury goods demand met");
        } else {
            market.getStability().unmodifyFlat(getModId(1));
        }

        deficit = getDeficitAmountPenalty(Commodities.FOOD);
        if (!market.hasCondition(Conditions.HABITABLE)) {
            if (getDeficitAmountPenalty(Commodities.ORGANICS).two > deficit.two) {
                deficit = getDeficitAmountPenalty(Commodities.ORGANICS);
            }
        }
        if (deficit.two > 0) {
            market.getStability()
                    .modifyFlat(
                            "aotd_shortage_food_organics",
                            -deficit.two,
                            getDeficitText(deficit.one));
        } else {
            market.getStability().unmodifyFlat("aotd_shortage_food_organics");
        }

        boolean spaceportFirstInQueue = false;
        for (ConstructionQueue.ConstructionQueueItem item :
                market.getConstructionQueue().getItems()) {
            IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(item.id);
            if (spec.hasTag(Industries.TAG_SPACEPORT)) {
                spaceportFirstInQueue = true;
            }
            break;
        }
        if (spaceportFirstInQueue && Misc.getCurrentlyBeingConstructed(market) != null) {
            spaceportFirstInQueue = false;
        }
        if (!market.hasSpaceport() && !spaceportFirstInQueue) {
            float accessibilityNoSpaceport =
                    Global.getSettings().getFloat("accessibilityNoSpaceport");
            market.getAccessibilityMod()
                    .modifyFlat(getModId(0), accessibilityNoSpaceport, "No spaceport");
        }

        float sizeBonus = getAccessibilityBonus(size);
        if (sizeBonus > 0) {
            market.getAccessibilityMod().modifyFlat(getModId(1), sizeBonus, "Colony size");
        }

        float stability = market.getPrevStability();
        float stabilityQualityMod = FleetFactoryV3.getShipQualityModForStability(stability);
        float doctrineQualityMod = market.getFaction().getDoctrine().getShipQualityContribution();

        market.getStats()
                .getDynamic()
                .getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(getModId(0), stabilityQualityMod, "Stability");

        market.getStats()
                .getDynamic()
                .getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlatAlways(
                        getModId(1),
                        doctrineQualityMod,
                        Misc.ucFirst(market.getFaction().getEntityNamePrefix())
                                + " fleet doctrine");

        // float stabilityDefenseMult = 0.5f + stability / 10f;
        float stabilityDefenseMult = 0.25f + stability / 10f * 0.75f;
        market.getStats()
                .getDynamic()
                .getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyMultAlways(getModId(), stabilityDefenseMult, "Stability");

        float baseDef = getBaseGroundDefenses(market.getSize());
        market.getStats()
                .getDynamic()
                .getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyFlatAlways(
                        getModId(),
                        baseDef,
                        "Base value for a size " + market.getSize() + " colony");

        // if (market.getHazardValue() > 1f) {
        if (HAZARD_INCREASES_DEFENSE) {
            market.getStats()
                    .getDynamic()
                    .getMod(Stats.GROUND_DEFENSES_MOD)
                    .modifyMultAlways(
                            getModId(1),
                            Math.max(market.getHazardValue(), 1f),
                            "Colony hazard rating");
        }
        // }

        market.getStats()
                .getDynamic()
                .getMod(Stats.MAX_INDUSTRIES)
                .modifyFlat(getModId(), getMaxIndustries(), null);

        //		if (market.isPlayerOwned()) {
        //			System.out.println("wfwefwef");
        //		}
        FactionDoctrineAPI doctrine = market.getFaction().getDoctrine();
        float doctrineShipsMult = FleetFactoryV3.getDoctrineNumShipsMult(doctrine.getNumShips());
        float marketSizeShipsMult = FleetFactoryV3.getNumShipsMultForMarketSize(market.getSize());
        float deficitShipsMult = FleetFactoryV3.getShipDeficitFleetSizeMult(market);
        float stabilityShipsMult = FleetFactoryV3.getNumShipsMultForStability(stability);

        market.getStats()
                .getDynamic()
                .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyFlatAlways(getModId(0), marketSizeShipsMult, "Colony size");

        market.getStats()
                .getDynamic()
                .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMultAlways(
                        getModId(1),
                        doctrineShipsMult,
                        Misc.ucFirst(market.getFaction().getEntityNamePrefix())
                                + " fleet doctrine");

        if (deficitShipsMult != 1f) {
            market.getStats()
                    .getDynamic()
                    .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId(2), deficitShipsMult, getDeficitText(Commodities.SHIPS));
        } else {
            market.getStats()
                    .getDynamic()
                    .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMultAlways(
                            getModId(2),
                            deficitShipsMult,
                            getDeficitText(Commodities.SHIPS).replaceAll("shortage", "demand met"));
        }

        market.getStats()
                .getDynamic()
                .getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMultAlways(getModId(3), stabilityShipsMult, "Stability");

        // chance of spawning officers and admins; some industries further modify this
        market.getStats()
                .getDynamic()
                .getMod(Stats.OFFICER_PROB_MOD)
                .modifyFlat(getModId(0), OFFICER_BASE_PROB);
        market.getStats()
                .getDynamic()
                .getMod(Stats.OFFICER_PROB_MOD)
                .modifyFlat(getModId(1), OFFICER_PROB_PER_SIZE * Math.max(0, market.getSize() - 3));

        market.getStats()
                .getDynamic()
                .getMod(Stats.OFFICER_ADDITIONAL_PROB_MULT_MOD)
                .modifyFlat(getModId(0), OFFICER_ADDITIONAL_BASE_PROB);
        market.getStats()
                .getDynamic()
                .getMod(Stats.OFFICER_IS_MERC_PROB_MOD)
                .modifyFlat(getModId(0), OFFICER_BASE_MERC_PROB);

        market.getStats()
                .getDynamic()
                .getMod(Stats.ADMIN_PROB_MOD)
                .modifyFlat(getModId(0), ADMIN_BASE_PROB);
        market.getStats()
                .getDynamic()
                .getMod(Stats.ADMIN_PROB_MOD)
                .modifyFlat(getModId(1), ADMIN_PROB_PER_SIZE * Math.max(0, market.getSize() - 3));

        modifyStability2(this, market, getModId(3));

        market.addTransientImmigrationModifier(this);

        //		// if there's no queued spaceport, setHasSpaceport() is called by Spaceport (if it's
        // present at the market)
        //		boolean spaceportFirstInQueue = false;
        //		for (ConstructionQueueItem item : market.getConstructionQueue().getItems()) {
        //			IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(item.id);
        //			if (spec.hasTag(Industries.TAG_SPACEPORT)) {
        //				market.setHasSpaceport(true);
        //				market.getMemoryWithoutUpdate().set("$hadQueuedSpaceport", true);
        //				spaceportFirstInQueue = true;
        //			}
        //			break;
        //		}
        //		if (!spaceportFirstInQueue && market.hasSpaceport() &&
        // market.getMemoryWithoutUpdate().is("$hadQueuedSpaceport", true)) {
        //			market.getMemoryWithoutUpdate().unset("$hadQueuedSpaceport");
        //			boolean hasSpaceport = false;
        //			for (Industry ind : market.getIndustries()) {
        //				if (ind.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
        //					hasSpaceport = true;
        //					break;
        //				}
        //			}
        //			if (!hasSpaceport) {
        //				market.setHasSpaceport(false);
        //			}
        //		}
        market.getStability().unmodifyFlat(getModId(2));
        market.getStats()
                .getDynamic()
                .getMod(AoTDMarketStats.AOTD_GRAND_WONDER_COUNT)
                .modifyFlat(
                        getModId(),
                        Math.max(Math.floorDiv(Misc.getMaxIndustries(market), 2), 1),
                        "Grand Wonder Slots");
        int overLimit =
                (int)
                        (GrandWonderManager.getAmountOfWonders(market)
                                - market.getStats()
                                        .getDynamic()
                                        .getMod(AoTDMarketStats.AOTD_GRAND_WONDER_COUNT)
                                        .computeEffective(0f));
        if (overLimit > 0) {

            market.getUpkeepMult()
                    .modifyMult("aotd_wonder_pen", overLimit + 1, "Grand wonder limit");
        }
    }

    @Override
    protected void addPostDemandSection(
            TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {

            MutableStat stabilityMods = new MutableStat(0);

            float total = 0;
            for (MutableStat.StatMod mod : market.getStability().getFlatMods().values()) {
                if (mod.source.startsWith(getModId())
                        || mod.source.equals("aotd_shortage_food_organics")) {
                    stabilityMods.modifyFlat(mod.source, mod.value, mod.desc);
                    total += mod.value;
                }
            }

            String totalStr = "+" + (int) Math.round(total);
            Color h = Misc.getHighlightColor();
            if (total < 0) {
                totalStr = "" + (int) Math.round(total);
                h = Misc.getNegativeHighlightColor();
            }
            float opad = 10f;
            float pad = 3f;
            if (total >= 0) {
                tooltip.addPara("Stability bonus: %s", opad, h, totalStr);
            } else {
                tooltip.addPara("Stability penalty: %s", opad, h, totalStr);
            }
            tooltip.addStatModGrid(
                    400,
                    30,
                    opad,
                    pad,
                    stabilityMods,
                    new TooltipMakerAPI.StatModValueGetter() {
                        public String getPercentValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public String getMultValue(MutableStat.StatMod mod) {
                            return null;
                        }

                        public Color getModColor(MutableStat.StatMod mod) {
                            if (mod.value < 0) return Misc.getNegativeHighlightColor();
                            return null;
                        }

                        public String getFlatValue(MutableStat.StatMod mod) {
                            return null;
                        }
                    });

            /*
            MutableStat qualityMods = new MutableStat(0);

            total = 0;
            for (StatMod mod : market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).getFlatBonuses().values()) {
                if (mod.source.startsWith(getModId())) {
                    qualityMods.modifyFlat(mod.source, mod.value, mod.desc);
                    total += mod.value;
                }
            }

            totalStr = "+" + (int)Math.round(total * 100f) + "%";
            if (total < 0) {
                totalStr = "" + (int)Math.round(total * 100f) + "%";
                h = Misc.getNegativeHighlightColor();
            }
            if (total >= 0) {
                tooltip.addPara("Ship quality bonus: %s", opad, h, totalStr);
            } else {
                tooltip.addPara("Ship quality penalty: %s", opad, h, totalStr);
            }
            tooltip.addStatModGrid(400, 50, opad, pad, qualityMods, new StatModValueGetter() {
                public String getPercentValue(StatMod mod) {
                    return null;
                }
                public String getMultValue(StatMod mod) {
                    return null;
                }
                public Color getModColor(StatMod mod) {
                    if (mod.value < 0) return Misc.getNegativeHighlightColor();
                    return null;
                }
                public String getFlatValue(StatMod mod) {
                    String prefix = mod.value >= 0 ? "+" : "";
                    return prefix + (int)Math.round(mod.value * 100f) + "%";
                }
            });
            */

        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStability().unmodifyFlat("aotd_shortage_food_organics");
        market.getStability().unmodifyFlat(getModId(2));
    }

    public static void modifyStabilityToolbox(Industry industry, MarketAPI market, String modId) {
        market.getIncomeMult()
                .modifyMultAlways(
                        modId, getIncomeStabilityMult(market.getPrevStability()), "Stability");

        market.getStability()
                .modifyFlat(
                        "_" + modId + "_ms",
                        Global.getSettings().getFloat("stabilityBaseValue"),
                        "Base value");
        if (Global.LOADING_SAVE) {
            AoTDEconomy.pruneCommoditiesThatMightAppear((Market) market);
        }

        if (market.getFaction().isPlayerFaction()) {
            String hehe = "he";
        }
        int amount = 0;
        float ratiosSoFar = 0f;
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            if (com.isNonEcon()) continue;
            if (com instanceof AoTDCommodityOnMarket toolboxCom) {
                int totalDemand = 0;
                int totalSupply = 0;
                totalDemand += toolboxCom.getSupplyDemandData().getTotalRawUnitsFromDemand();
                totalSupply += toolboxCom.getSupplyDemandData().getTotalRawUnitsFromSupply();
                AoTDMarketData marketData = AoTDTradeManager.getInstance().getMarketData(market);
                if (marketData != null) {
                    totalSupply += marketData.getInternalImported(com.getId());
                }
                if (totalDemand == 0) continue;

                float ratio = (float) totalSupply / totalDemand;
                ratiosSoFar += ratio;
                amount++;
            }
        }

        if (amount > 0) {
            float max = Global.getSettings().getFloat("upkeepReductionFromInFactionImports");
            float f = ratiosSoFar / amount;
            if (f < 0) f = 0;
            if (f > 1) f = 1;
            if (f > 0) {
                float mult = Math.round(100f - (f * max * 100f)) / 100f;
                String desc = "Demand supplied in-faction (" + (int) Math.round(f * 100f) + "%)";
                if (f == 1f) desc = "All demand supplied in-faction";
                market.getUpkeepMult().modifyMultAlways(modId + "ifi", mult, desc);
            } else {
                market.getUpkeepMult()
                        .modifyMultAlways(
                                modId + "ifi",
                                1f,
                                "All demand supplied out-of-faction; no upkeep reduction");
            }
        }

        if (market.isPlayerOwned() && market.getAdmin().isPlayer()) {
            int penalty = getMismanagementPenalty();
            if (penalty > 0) {
                market.getStability()
                        .modifyFlat("_" + modId + "_mm", -penalty, "Mismanagement penalty");
            } else if (penalty < 0) {
                market.getStability().modifyFlat("_" + modId + "_mm", -penalty, "Management bonus");
            } else {
                market.getStability().unmodifyFlat("_" + modId + "_mm");
            }
        } else {
            market.getStability().unmodifyFlat(modId + "_mm");
        }

        if (!market.hasCondition(Conditions.COMM_RELAY)) {
            market.getStability()
                    .modifyFlat(
                            CommRelayCondition.COMM_RELAY_MOD_ID,
                            CommRelayCondition.NO_RELAY_PENALTY,
                            "No active comm relay in-system");
        }
    }
}
