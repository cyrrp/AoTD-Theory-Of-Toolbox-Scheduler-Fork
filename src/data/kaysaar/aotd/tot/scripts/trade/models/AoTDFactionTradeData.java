package data.kaysaar.aotd.tot.scripts.trade.models;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomy;
import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.economy.AoTDWorkerManager;
import data.kaysaar.aotd.tot.scripts.trade.contracts.AoTDTradeContractManager;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionCycleProductionData;
import data.kaysaar.aotd.tot.scripts.trade.history.FactionProductionData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AoTDFactionTradeData {

    private final LinkedHashMap<String, AoTDMarketData> tradeData = new LinkedHashMap<>();
    private final String faction;

    protected LinkedHashMap<Integer, FactionCycleProductionData> cycleProductionData;
    public int startingCycleOfData = 0;
    public int startingMonthOfCycle = 0;
    public boolean hasSetStartingDate = false;

    public AoTDFactionTradeData(String faction) {
        this.faction = faction;
        this.cycleProductionData = new LinkedHashMap<>();
    }

    public void removeMarket(MarketAPI market) {
        if (market != null && removeMarketSnapshot(market.getId())) {
            computeInternalTrade(); // rebuild remainingNet
        }
    }

    /** Removes only the authoritative snapshot; settlement is a later phase. */
    public boolean removeMarketSnapshot(String marketId) {
        return marketId != null && tradeData.remove(marketId) != null;
    }

    private boolean isBeforeStartOfHistory(int cycle, int month) {
        if (!hasSetStartingDate) return true;

        if (cycle < startingCycleOfData) return true;
        return cycle == startingCycleOfData && month < startingMonthOfCycle;
    }

    public void doEndOfMonthStuffForHistory(int month) {
        if (!hasSetStartingDate) {
            hasSetStartingDate = true;
            startingCycleOfData = Global.getSector().getClock().getCycle();
            if (month == -1) {
                startingCycleOfData = Global.getSector().getClock().getCycle() - 1;
                month = 12;
                startingMonthOfCycle = 12;
                FactionCycleProductionData productionData = new FactionCycleProductionData(faction);

                this.cycleProductionData.put(startingCycleOfData, productionData);
                productionData.doEndOfMonth(month);
            }
        } else {
            if (!cycleProductionData.containsKey(Global.getSector().getClock().getCycle())) {
                FactionCycleProductionData productionData = new FactionCycleProductionData(faction);
                this.cycleProductionData.put(
                        Global.getSector().getClock().getCycle(), productionData);
            }
            FactionCycleProductionData productionData =
                    cycleProductionData.get(Global.getSector().getClock().getCycle());
            productionData.doEndOfMonth(month);
        }
    }

    public ArrayList<Integer> getProductionFromMonths(String commodityId) {
        return getProductionFromMonths(
                Global.getSector().getClock().getCycle(),
                Global.getSector().getClock().getMonth() - 1,
                commodityId,
                Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getDemandFromMonths(String commodityId) {
        return getDemandFromMonths(
                Global.getSector().getClock().getCycle(),
                Global.getSector().getClock().getMonth() - 1,
                commodityId,
                Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getProductionFromMonths(int months, String commodityId) {
        return getProductionFromMonths(
                Global.getSector().getClock().getCycle(),
                Global.getSector().getClock().getMonth() - 1,
                commodityId,
                months);
    }

    public ArrayList<Integer> getDemandFromMonths(int months, String commodityId) {
        return getDemandFromMonths(
                Global.getSector().getClock().getCycle(),
                Global.getSector().getClock().getMonth() - 1,
                commodityId,
                months);
    }

    public ArrayList<Integer> getProductionFromMonths(
            int startingCycle, int startingMonth, String commodityId) {
        return getProductionFromMonths(
                startingCycle, startingMonth, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getDemandFromMonths(
            int startingCycle, int startingMonth, String commodityId) {
        return getDemandFromMonths(startingCycle, startingMonth, commodityId, Integer.MAX_VALUE);
    }

    public ArrayList<Integer> getProductionFromMonths(
            int startingCycle, int startingMonth, String commodityId, int monthsBack) {
        ArrayList<Integer> result = new ArrayList<>();
        if (monthsBack <= 0) return result;

        if (!hasSetStartingDate) return result;

        int cycle = startingCycle;
        int month = startingMonth;
        if (startingMonth <= 0) {
            cycle--;
            month = 12;
        }
        for (int i = 0; i < monthsBack; i++) {
            if (isBeforeStartOfHistory(cycle, month)) break;

            int val = 0;

            FactionCycleProductionData cycleData = cycleProductionData.get(cycle);
            if (cycleData != null) {
                FactionProductionData monthData = cycleData.getProductionFromMonth(month);
                if (monthData != null) {
                    Integer v = monthData.getProductionValueFromMonth(commodityId);
                    if (v != null) val = v;
                }
            }

            result.add(val);

            month--;
            if (month < 1) {
                month = 12;
                cycle--;
            }
        }

        Collections.reverse(result);
        return result;
    }

    public ArrayList<Integer> getDemandFromMonths(
            int startingCycle, int startingMonth, String commodityId, int monthsBack) {
        ArrayList<Integer> result = new ArrayList<>();
        if (monthsBack <= 0) return result;
        if (!hasSetStartingDate) return result;

        int cycle = startingCycle;
        int month = startingMonth;
        if (startingMonth <= 0) {
            cycle--;
            month = 12;
        }

        for (int i = 0; i < monthsBack; i++) {
            if (isBeforeStartOfHistory(cycle, month)) break;

            int val = 0;

            FactionCycleProductionData cycleData = cycleProductionData.get(cycle);
            if (cycleData != null) {
                FactionProductionData monthData = cycleData.getProductionFromMonth(month);
                if (monthData != null) {
                    Integer v = monthData.getDemandValueFromMonth(commodityId);
                    if (v != null) val = v;
                }
            }

            result.add(val);

            month--;
            if (month < 1) {
                month = 12;
                cycle--;
            }
        }

        Collections.reverse(result);
        return result;
    }

    public void addMarket(MarketAPI market) {
        putMarketSnapshot(new AoTDMarketData(market));
    }

    public void putMarketSnapshot(AoTDMarketData snapshot) {
        if (snapshot != null && snapshot.marketId != null) {
            tradeData.put(snapshot.marketId, snapshot);
        }
    }

    public void reset() {
        tradeData.clear();
    }

    public FactionAPI getFaction() {
        return Global.getSector().getFaction(faction);
    }

    public int getFactionEffectiveDemand(String commodityId) {
        int net = 0;
        for (AoTDMarketData md : tradeData.values()) {
            net += md.netProductionValues.getOrDefault(commodityId, 0);
        }
        return Math.max(0, -net);
    }

    public int getFactionDemand(String commodityId) {

        return AoTDSectorProductionDemandDataUtils.getTotalDemandFromFaction(commodityId, faction);
    }

    public int getFactionSupply(String commodityId) {

        return AoTDSectorProductionDemandDataUtils.getTotalProductionFromFaction(
                commodityId, faction);
    }

    // ---------- internal trade solver ----------

    private static final class MarketAmount {
        final AoTDMarketData m;
        int amount;
        final float weight;

        MarketAmount(AoTDMarketData m, int amount, float weight) {
            this.m = m;
            this.amount = amount;
            this.weight = weight;
        }
    }

    private static final class CommodityBucket {
        final ArrayList<MarketAmount> exporters = new ArrayList<>();
        final ArrayList<MarketAmount> importers = new ArrayList<>();
        int totalSupply;
        int totalNeed;
    }

    /**
     * Computes internal trade and updates remainingNet. ALSO: if this is player faction, it
     * invalidates and (optionally) precomputes contract predictions, because remainingNet is what
     * contracts draw from.
     */
    private static final java.util.Comparator<MarketAmount> MARKET_AMOUNT_WEIGHT_DESC =
            (a, b) -> Float.compare(b.weight, a.weight);

    public void computeInternalTrade() {
        computeInternalTrade(true);
    }

    public void computeInternalTrade(boolean refreshContractPredictions) {
        if (tradeData.isEmpty()) return;

        ArrayList<AoTDMarketData> eligibleMarkets = new ArrayList<>(tradeData.size());

        for (AoTDMarketData md : tradeData.values()) {
            md.resetInternalResults();

            if (md.netProductionValues.isEmpty()) continue;

            MarketAPI market = AoTDEconomy.getInstance().getMarketThreadSave(md.marketId);
            if (market == null) continue;
            if (!market.hasSpaceport()) continue;
            if (market.getAccessibilityMod().computeEffective(0f) <= 0f) continue;

            eligibleMarkets.add(md);
        }

        if (eligibleMarkets.size() <= 1) {
            if (refreshContractPredictions) {
                refreshContractPredictionsIfPlayerFaction();
            }

            return;
        }

        LinkedHashMap<String, CommodityBucket> buckets = new LinkedHashMap<>();

        for (AoTDMarketData md : eligibleMarkets) {
            AoTDWorkerManager.checkpoint();
            for (Map.Entry<String, Integer> entry : md.netProductionValues.entrySet()) {
                int net = entry.getValue();
                if (net == 0) continue;

                CommodityBucket bucket =
                        buckets.computeIfAbsent(entry.getKey(), id -> new CommodityBucket());

                if (net > 0) {
                    bucket.exporters.add(new MarketAmount(md, net, md.weight));
                    bucket.totalSupply += net;
                } else {
                    int need = -net;
                    bucket.importers.add(new MarketAmount(md, need, md.weight));
                    bucket.totalNeed += need;
                }
            }
        }

        for (Map.Entry<String, CommodityBucket> entry : buckets.entrySet()) {
            AoTDWorkerManager.checkpoint();
            CommodityBucket bucket = entry.getValue();

            if (bucket.totalSupply <= 0 || bucket.totalNeed <= 0) continue;

            ArrayList<MarketAmount> exporters = bucket.exporters;
            ArrayList<MarketAmount> importers = bucket.importers;

            if (exporters.size() > 1) exporters.sort(MARKET_AMOUNT_WEIGHT_DESC);
            if (importers.size() > 1) importers.sort(MARKET_AMOUNT_WEIGHT_DESC);

            String commodityId = entry.getKey();

            int exporterIndex = 0;
            int importerIndex = 0;

            while (exporterIndex < exporters.size() && importerIndex < importers.size()) {
                MarketAmount exporter = exporters.get(exporterIndex);
                MarketAmount importer = importers.get(importerIndex);

                int moved = Math.min(exporter.amount, importer.amount);
                if (moved <= 0) {
                    if (exporter.amount <= 0) exporterIndex++;
                    if (importer.amount <= 0) importerIndex++;
                    continue;
                }

                exporter.m.addInternalSent(commodityId, moved);
                importer.m.addInternalReceived(commodityId, moved);

                exporter.amount -= moved;
                importer.amount -= moved;

                if (exporter.amount <= 0) exporterIndex++;
                if (importer.amount <= 0) importerIndex++;
            }
        }

        if (refreshContractPredictions) {
            refreshContractPredictionsIfPlayerFaction();
        }
    }

    public void refreshContractPredictionsIfPlayerFaction() {
        String playerFactionId = Global.getSector().getPlayerFaction().getId();
        if (!faction.equals(playerFactionId)) return;

        AoTDTradeContractManager mgr = AoTDTradeContractManager.getInstance();
        mgr.invalidatePredictions();
        mgr.ensurePredictionsUpToDate();
    }

    public LinkedHashMap<String, AoTDMarketData> getTradeData() {
        return tradeData;
    }
}
