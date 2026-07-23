package data.kaysaar.aotd.tot.misc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContract;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils.getPriceForAmount;

public class AoTDToolboxMisc {
    public static String getDGSStringWithSign(int number) {

        String plus = "";
        if (number > 0) {
            plus = "+";
        }
        return plus + Misc.getWithDGS(number);
    }
    public static void setIndustryOnPlanet(String SystemName, String Planetname, String industryId, String removeIndustry, String potentialSwitch, boolean toImprove, String aiCore, String itemToInsert) {
        if (Global.getSector().getStarSystem(SystemName) == null) return;
        List<PlanetAPI> planets = Global.getSector().getStarSystem(SystemName).getPlanets();
        for (PlanetAPI planet : planets) {
            if (planet.getName().equals(Planetname)) {

                if (planet.getMarket() == null) continue;
                MarketAPI mutationMarket = planet.getMarket();
                long token = SchedulerBridge.beforeMarketMutation(
                        mutationMarket, SchedulerBridge.MUTATION_INDUSTRY_STRUCTURE);
                try {
                    SpecialItemData data = null;
                    if (removeIndustry != null && mutationMarket.getIndustry(removeIndustry) != null) {
                        data = mutationMarket.getIndustry(removeIndustry).getSpecialItem();
                        mutationMarket.removeIndustry(removeIndustry, null, false);
                    }
                    if (industryId != null) {
                        mutationMarket.addIndustry(industryId);
                        mutationMarket.getIndustry(industryId).setImproved(toImprove);
                        mutationMarket.getIndustry(industryId).setAICoreId(aiCore);
                        if (data != null) {
                            mutationMarket.getIndustry(industryId).setSpecialItem(data);
                        }
                        if (itemToInsert != null) {
                            SpecialItemData daten = new SpecialItemData(itemToInsert, null);
                            mutationMarket.getIndustry(industryId).setSpecialItem(daten);
                        }
                    }
                } finally {
                    SchedulerBridge.afterMarketMutation(token, mutationMarket,
                            SchedulerBridge.DIRTY_STRUCTURE
                                    | SchedulerBridge.DIRTY_INDUSTRIES
                                    | SchedulerBridge.DIRTY_DERIVED_ECONOMY, 0L);
                }


            }
        }
    }
    public static int getAmountOfMarketsGreaterAccThanTargetedMarket(List<MarketAPI>markets, MarketAPI market){
        return markets.stream().filter(x->x.getAccessibilityMod().computeEffective(0f)>market.getAccessibilityMod().computeEffective(0f)).toList().size();
    }
    public static String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    public static int getAmountOfContractsOfSameType(String typeId) {
        int am = 0;
        for (AoTDTradeContract activeContract : AoTDTradeContractManager.getInstance().getActiveContracts().values()) {
            if (activeContract.getContractTypeId().equals(typeId)) {
                am++;
            }
        }
        return am;
    }

    public static int getExpectedMonthlyIncomeForMarket(MarketAPI market) {
        if (market == null) return 0;

        double guaranteed = market.getIndustryIncome(); // stays as-is
        double expectedExport = 0.0;

        for (CommodityOnMarketAPI c : market.getAllCommodities()) {
            if (c instanceof AoTDCommodityOnMarket a) {
                expectedExport += getExpectedMonthlyIncomeFromCommodity(a);
            }
        }

        // If you want a "net" style number, you can subtract upkeep etc. here too,
        // but you only asked for expected monthly income.
        return (int) Math.floor(guaranteed + expectedExport);
    }

    public static boolean isContractMetFully(AoTDTradeContract contract) {
        for (AoTDTradeContract.TradeContractData value : contract.getContractData().values()) {
            int prod = AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(value.getCommodityId(), Factions.PLAYER);
            int dem = AoTDSectorProductionDemandDataUtils.getTotalDemandFromFactionTillContract(value.getCommodityId(), Factions.PLAYER, contract.getId());
            if (dem > prod) {
                return false;
            }
        }
        return true;
    }

    public static float getDeficitCountered(AoTDCommodityOnMarket commodity) {
        float countered = 0;
        for (Map.Entry<String, MutableStat.StatMod> entry : commodity.getExcDefData().deficit.getFlatMods().entrySet()) {
            if (entry.getKey().contains("aotd_shortage_counter")) {
                countered += Math.abs(entry.getValue().value);
            }
        }
        return countered;
    }

    public static float getStockpileCost(MarketAPI market) {
        List<CommodityOnMarketAPI> all = new ArrayList<CommodityOnMarketAPI>(market.getAllCommodities());

        float totalCost = 0f;

        for (CommodityOnMarketAPI commodity : all) {
            if (commodity instanceof AoTDCommodityOnMarket com) {
                float units = getDeficitCountered(com);
                if (units > 0) {
                    float per = LocalResourcesSubmarketPlugin.getStockpilingUnitPrice(com.getSpec(), true);
                    totalCost += units * per;
                }
            }
        }
        return (int) totalCost;
    }

    public static float getExpectedMonthlyNetIncomeFromMarket(MarketAPI market) {
        if (market == null) return 0f;

        double totalExportIncome = 0.0;

        // Sum expected export income from all AoTD commodities
        for (CommodityOnMarketAPI c : market.getAllCommodities()) {
            if (c instanceof AoTDCommodityOnMarket a) {
                totalExportIncome += getExpectedMonthlyIncomeFromCommodity(a);
            }
        }

        double industryIncome = market.getIndustryIncome();
        double upkeep = market.getIndustryUpkeep();

        double shortageCost = 0.0;
        shortageCost = market.getShortageCounteringCost();


        double immigrationCost = 0.0;
        if (market.isImmigrationIncentivesOn()) {
            immigrationCost = market.getImmigrationIncentivesCost();
        }

        double net =
                industryIncome
                        + totalExportIncome
                        - upkeep
                        - shortageCost
                        - immigrationCost;

        return (float) net;
    }

    public static int getGuaranteedExportIncome(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;
        if (!com.isDemandLegal() || !com.isSupplyLegal()) return 0;

        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(com.getMarket());
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();


        // only internal shipments (guaranteed by your internal solver)
        int internalUnits = Math.max(0, md.getInternalExported(com.getId()));
        int supply = com.getSupplyDemandData().getTotalRawUnitsFromSupply();
        int demand = com.getSupplyDemandData().getTotalRawUnitsFromDemand();
        int suppliedLocally = Math.min(supply, demand);
        double localIncome = 0.0;
        if (suppliedLocally > 0) {
            localIncome = suppliedLocally * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), true);
        }
        double income = internalUnits * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), true);
        return (int) Math.floor(income + localIncome);
    }

    public static int getIncomeFromSelling(AoTDCommodityOnMarket com) {
        int guaranteed = getGuaranteedExportIncome(com);
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(com.getMarket());
        if (md == null) return guaranteed;
        int exported = md.getSoldOutside(com.getSpec().getId());
        int extraExported = md.getExtraSoldOutside(com.getSpec().getId());
        float cut = AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), false);

        int extraIncome = Math.round(getPriceForAmount(com.getId(), extraExported) * AoTDTradeManager.multFromSellingExcess);

        int income = Math.round((exported * cut * com.getSpec().getBasePrice()));
        return income + guaranteed+extraIncome;

    }

    public static int getSpeculatedExportIncome(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;
        if (!com.isSupplyLegal() || !com.isDemandLegal()) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();

        // total potential export (your existing metric)
        int export = com.getSupplyDemandData().getExportExcludingDeficit();
        if (export <= 0) return 0;

        // guaranteed internal part
        int internalUnits = Math.max(0, md.getInternalExported(com.getId()));

        // ---- Predicted contract income (UI/speculation) ----
        // Make sure prediction cache is built for current month
        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        int predictedContractUnits = 0;
        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active != null && !active.isEmpty()) {
            for (AoTDTradeContract c : active.values()) {
                if (c == null) continue;
                if (c.isExpired() || c.isTerminated()) continue;

                if (!c.getContractData().containsKey(com.getId())) continue;

                int units = md.getPredictedContractExported(c.getId(), com.getId());
                if (units > 0) predictedContractUnits += units;
            }
        }

        // remaining export pool that could go to external markets (speculated)
        int externalPool = export - internalUnits - predictedContractUnits;
        if (externalPool < 0) externalPool = 0;
        int producitonOutside = AoTDSectorProductionDemandDataUtils.getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts(com.getSpec().getId(), market.getFactionId());
        externalPool = Math.min(externalPool, producitonOutside);
        // external only pays if there is demand outside faction
        boolean hasOutsideDemand = producitonOutside>0;

        double externalIncome = 0.0;
        if (hasOutsideDemand && externalPool > 0) {
            externalIncome = externalPool * basePrice * AoTDCommodityEconSpecManager.getCutForCommodity(com.getSpec().getId(), false);
        }

        double total = externalIncome;
        return (int) Math.floor(total);
    }
    public static int getSpeculatedExportIncomeFromContractsForUI(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();

        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active == null || active.isEmpty()) return 0;

        double predicted = 0.0;

        for (AoTDTradeContract c : active.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;
            AoTDTradeContract.TradeContractData line = c.getContractData().get(com.getId());
            if (line == null) continue;

            int units = md.getPredictedContractExported(c.getId(), com.getId());
            if (units <= 0) continue;

            float cut = Math.max(0f, line.getCutFromBasePrice());
            predicted += units * basePrice * (cut);
        }
        return (int) Math.floor(predicted);
    }

    public static int getSpeculatedExportIncomeFromContracts(AoTDCommodityOnMarket com) {
        if (com == null || com.getMarket() == null) return 0;

        MarketAPI market = com.getMarket();
        AoTDMarketData md = AoTDTradeManager.getInstance().getMarketData(market);
        if (md == null) return 0;

        float basePrice = com.getSpec().getBasePrice();

        AoTDTradeContractManager.getInstance().ensurePredictionsUpToDate();

        Map<String, AoTDTradeContract> active = AoTDTradeContractManager.getInstance().getActiveContracts();
        if (active == null || active.isEmpty()) return 0;

        double predicted = 0.0;

        for (AoTDTradeContract c : active.values()) {
            if (c == null) continue;
            if (c.isExpired() || c.isTerminated()) continue;
            if (!c.isContractEarningIncome()) continue;
            AoTDTradeContract.TradeContractData line = c.getContractData().get(com.getId());
            if (line == null) continue;

            int units = md.getPredictedContractExported(c.getId(), com.getId());
            if (units <= 0) continue;

            float cut = Math.max(0f, line.getCutFromBasePrice());
            predicted += units * basePrice * (cut);
        }
        return (int) Math.floor(predicted);
    }

    public static int getExpectedMonthlyIncomeFromCommodity(AoTDCommodityOnMarket commodity) {
        if (commodity == null) return 0;
        return getGuaranteedExportIncome(commodity) + getSpeculatedExportIncome(commodity) + getSpeculatedExportIncomeFromContracts(commodity);
    }

    public static LinkedHashSet<FactionAPI> getFactionsInEconomy() {
        LinkedHashSet<FactionAPI> factions = new LinkedHashSet<>();
        for (MarketAPI marketAPI : Global.getSector().getEconomy().getMarketsCopy()) {
            factions.add(marketAPI.getFaction());
        }
        return factions;
    }

    public static int getMaxShipped(MarketAPI marketFrom, MarketAPI towards, String commodityId) {
        AoTDCommodityOnMarket com = AoTDCommodityOnMarket.getComMarketInstanceSave(marketFrom,commodityId);
        if (marketFrom.getFaction().equals(towards.getFaction())) {
            AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(marketFrom);
            int exportedToFaction = 0;
            if (data != null) {
                data.getInternalExported(commodityId);
            }
            return Math.max(exportedToFaction, com.getSupplyDemandData().getExportExcludingDeficit() - exportedToFaction);
        } else {
            AoTDMarketData data = AoTDTradeManager.getInstance().getMarketData(marketFrom);
            int exportedToFaction = 0;
            if (data != null) {
                data.getInternalExported(commodityId);
            }
            return Math.max(0, com.getSupplyDemandData().getExportExcludingDeficit() - exportedToFaction);
        }
    }

    public static int getMaxImported(MarketAPI marketFrom, MarketAPI towards, String commodityId) {
        AoTDCommodityOnMarket com = (AoTDCommodityOnMarket) marketFrom.getCommodityData(commodityId);
        return com.getSupplyDemandData().getTotalRawUnitsFromDemand() - com.getSupplyDemandData().getTotalRawUnitsFromSupply();
    }
}
