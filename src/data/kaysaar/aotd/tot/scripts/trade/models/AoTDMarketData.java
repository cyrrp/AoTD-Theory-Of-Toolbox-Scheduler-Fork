// file: data/kaysaar/aotd/tot/scripts/trade/models/AoTDMarketData.java
package data.kaysaar.aotd.tot.scripts.trade.models;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public class AoTDMarketData {
    public static final int CHANGE_ACCESSIBILITY = 1;
    public static final int CHANGE_ELIGIBILITY = 1 << 1;
    public static final int CHANGE_NET_PRODUCTION = 1 << 2;
    public static final int FINGERPRINT_VERSION = 1;

    public String marketId;
    public Object readResolve(){
        if(soldOutside==null){
            soldOutside = new LinkedHashMap<>();
        }
        if(extraSold==null){
            extraSold = new LinkedHashMap<>();
        }
        if (tradeFingerprintVersion <= 0) {
            tradeFingerprint = computeTradeFingerprint();
            tradeFingerprintVersion = FINGERPRINT_VERSION;
        }
        return this;
    }

    /** Net export (positive) / net import demand (negative). Snapshot at build time. */
    public  LinkedHashMap<String, Integer> netProductionValues = new LinkedHashMap<>();
    public LinkedHashMap<String,Integer>extraSold = new LinkedHashMap<>();
    /** Internal trade results. */
    public  LinkedHashMap<String, Integer> internalSent = new LinkedHashMap<>();
    public  LinkedHashMap<String, Integer> internalReceived = new LinkedHashMap<>();

    public  LinkedHashMap<String,Integer>soldOutside = new LinkedHashMap<>();
    /** Remaining net after internal trade / contracts / external trade bookkeeping. */
    public  LinkedHashMap<String, Integer> remainingNet = new LinkedHashMap<>();

    /**
     * Amount of export removed by the surplus-cap AFTER matching.
     * Used for producer bonuses.
     */
    public  LinkedHashMap<String, Integer> externalExcessExported = new LinkedHashMap<>();

    /** Actual: contractId -> (commodityId -> amount shipped for that contract THIS MONTH) */
    public  LinkedHashMap<String, LinkedHashMap<String, Integer>> exportedByContract = new LinkedHashMap<>();

    /** Predicted: contractId -> (commodityId -> amount that WOULD be shipped this month) */
    public  LinkedHashMap<String, LinkedHashMap<String, Integer>> predictedExportedByContract = new LinkedHashMap<>();

    /** Base weights (per-market). */
    public float weight;
    public float outsideWeight;
    /** Eligibility captured on the campaign thread; workers never query MarketAPI. */
    public boolean internalTradeEligible;
    /** Local publication revision assigned by AoTDTradeManager. */
    public long publicationRevision;
    /** Stable diagnostic fingerprint; exact equality is still verified before skipping publication. */
    public long tradeFingerprint;
    public int tradeFingerprintVersion;

    /** Normal constructor from the last committed local supply/demand revision. */
    public AoTDMarketData(MarketAPI market) {
        this(market, captureCommittedNetProduction(market));
    }

    private AoTDMarketData(MarketAPI market, LinkedHashMap<String, Integer> netProduction) {
        this.marketId = market.getId();
        this.netProductionValues.putAll(netProduction);
        this.remainingNet.putAll(netProduction);

        float accessibility = market.getAccessibilityMod().computeEffective(0f);
        this.weight = accessibility * 100f;
        this.outsideWeight = Math.max(accessibility * 100f, 20f);
        this.internalTradeEligible = market.hasSpaceport() && accessibility > 0f;
        this.tradeFingerprint = computeTradeFingerprint();
        this.tradeFingerprintVersion = FINGERPRINT_VERSION;
    }

    /**
     * Captures trade inputs after ImmigrationTask directly from current live
     * industry stats. The method does not publish or mutate supply/demand data.
     */
    public static AoTDMarketData capturePostImmigration(MarketAPI market) {
        LinkedHashMap<String, Integer> netProduction = new LinkedHashMap<>();
        for (CommodityOnMarketAPI commodity : market.getAllCommodities()) {
            if (!(commodity instanceof AoTDCommodityOnMarket aotdCommodity)) continue;
            int net = aotdCommodity.getSupplyDemandData()
                    .computeRawNetForTradeSnapshot(market);
            if (net != 0) netProduction.put(aotdCommodity.getId(), net);
        }
        return new AoTDMarketData(market, netProduction);
    }

    private static LinkedHashMap<String, Integer> captureCommittedNetProduction(MarketAPI market) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (CommodityOnMarketAPI allCommodity : market.getAllCommodities()) {
            if (allCommodity instanceof AoTDCommodityOnMarket com) {
                int net = com.getSupplyDemandData().getRawNetExport();
                if (net != 0) result.put(com.getId(), net);
            }
        }
        return result;
    }

    /** Exact comparison; the hash is diagnostic only and never the sole correctness gate. */
    public boolean hasSameTradeInputs(AoTDMarketData other) {
        if (other == null) return false;
        return marketId != null && marketId.equals(other.marketId)
                && Float.floatToIntBits(weight) == Float.floatToIntBits(other.weight)
                && Float.floatToIntBits(outsideWeight) == Float.floatToIntBits(other.outsideWeight)
                && internalTradeEligible == other.internalTradeEligible
                && netProductionValues.equals(other.netProductionValues);
    }

    public int changeMaskComparedTo(AoTDMarketData previous) {
        if (previous == null) {
            return CHANGE_ACCESSIBILITY | CHANGE_ELIGIBILITY | CHANGE_NET_PRODUCTION;
        }
        int mask = 0;
        if (Float.floatToIntBits(weight) != Float.floatToIntBits(previous.weight)
                || Float.floatToIntBits(outsideWeight)
                != Float.floatToIntBits(previous.outsideWeight)) {
            mask |= CHANGE_ACCESSIBILITY;
        }
        if (internalTradeEligible != previous.internalTradeEligible) {
            mask |= CHANGE_ELIGIBILITY;
        }
        if (!netProductionValues.equals(previous.netProductionValues)) {
            mask |= CHANGE_NET_PRODUCTION;
        }
        return mask;
    }

    private long computeTradeFingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mixString(hash, marketId);
        hash = mixInt(hash, Float.floatToIntBits(weight));
        hash = mixInt(hash, Float.floatToIntBits(outsideWeight));
        hash = mixInt(hash, internalTradeEligible ? 1 : 0);
        List<String> commodityIds = new ArrayList<>(netProductionValues.keySet());
        Collections.sort(commodityIds);
        for (String commodityId : commodityIds) {
            hash = mixString(hash, commodityId);
            hash = mixInt(hash, netProductionValues.getOrDefault(commodityId, 0));
        }
        return hash;
    }

    private static long mixString(long hash, String value) {
        if (value == null) return mixInt(hash, 0);
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mixInt(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= (value >>> shift) & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Dummy constructor for synthetic offers (e.g., scavengers). */
    private AoTDMarketData(String id) {
        this.marketId = id;
        this.weight = 0f;
        this.outsideWeight = 0f;
        this.internalTradeEligible = false;
        this.tradeFingerprint = computeTradeFingerprint();
        this.tradeFingerprintVersion = FINGERPRINT_VERSION;
    }

    public static AoTDMarketData createScavengerDummy() {
        return new AoTDMarketData("SCAVENGER_GUILD");
    }

    /** Applies one pure internal-trade result atomically on the campaign thread. */
    public void applyInternalTradeResult(AoTDInternalTradeBatch.MarketResult result) {
        if (result == null || marketId == null || !marketId.equals(result.marketId)) return;
        internalSent.clear();
        internalSent.putAll(result.internalSent);
        internalReceived.clear();
        internalReceived.putAll(result.internalReceived);
        remainingNet.clear();
        remainingNet.putAll(result.remainingNet);
    }

    /** Immutable worker input captured from this committed snapshot. */
    public AoTDInternalTradeBatch.MarketInput toInternalTradeInput() {
        return new AoTDInternalTradeBatch.MarketInput(
                marketId, weight, internalTradeEligible, netProductionValues);
    }

    /** Reset internal results + remainingNet back to original snapshot. */
    public void resetInternalResults() {
        internalSent.clear();
        internalReceived.clear();
        remainingNet.clear();
        remainingNet.putAll(netProductionValues);
    }

    /** Reset per-month external accounting (excess-exported tracking). */
    public void resetExternalResults() {
        externalExcessExported.clear();
        soldOutside.clear();
        extraSold.clear();

    }

    /** Call once per month before contracts. (Actual shipments) */
    public void resetContractResults() {
        exportedByContract.clear();
    }

    /** Called by contract execution (actual shipments). */
    public void recordContractExport(String contractId, String commodityId, int amount) {
        if (amount <= 0) return;
        if (contractId == null || commodityId == null) return;

        exportedByContract
                .computeIfAbsent(contractId, k -> new LinkedHashMap<>())
                .merge(commodityId, amount, Integer::sum);
    }

    public int getContractExported(String contractId, String commodityId) {
        LinkedHashMap<String, Integer> m = exportedByContract.get(contractId);
        if (m == null) return 0;
        return m.getOrDefault(commodityId, 0);
    }

    // ------------------- PREDICTIONS -------------------

    /** Call when prediction manager rebuilds. */
    public void resetContractPredictions() {
        predictedExportedByContract.clear();
    }

    /** Called by prediction dry-run. */
    public void recordPredictedContractExport(String contractId, String commodityId, int amount) {
        if (amount <= 0) return;
        if (contractId == null || commodityId == null) return;

        predictedExportedByContract
                .computeIfAbsent(contractId, k -> new LinkedHashMap<>())
                .merge(commodityId, amount, Integer::sum);
    }

    public int getPredictedContractExported(String contractId, String commodityId) {
        LinkedHashMap<String, Integer> m = predictedExportedByContract.get(contractId);
        if (m == null) return 0;
        return m.getOrDefault(commodityId, 0);
    }

    // ------------------- READ HELPERS -------------------

    public int getOriginalNet(String commodityId) {
        return netProductionValues.getOrDefault(commodityId, 0);
    }

    public int getInternalExported(String commodityId) {
        return internalSent.getOrDefault(commodityId, 0);
    }

    public int getInternalImported(String commodityId) {
        return internalReceived.getOrDefault(commodityId, 0);
    }

    public int getRemainingNet(String commodityId) {
        return remainingNet.getOrDefault(commodityId, 0);
    }
    public int getExtraSoldOutside(String commodityId){return extraSold.getOrDefault(commodityId, 0);}
    public int getSoldOutside(String commodityId){return soldOutside.getOrDefault(commodityId, 0);}
    public int getExternalExcessExported(String commodityId) {
        return externalExcessExported.getOrDefault(commodityId, 0);
    }
    public void addExtraSold(String commodityId, int amount) {
        int curr = getExtraSoldOutside(commodityId);
        extraSold.put(commodityId, curr + amount);
    }

    // ------------------- INTERNAL APPLICATION -------------------
    public void addSoldOutside(String commodityId, int amount) {
        int curr = getSoldOutside(commodityId);
        soldOutside.put(commodityId, curr + amount);
    }
    public void addInternalSent(String commodityId, int amount) {
        if (amount <= 0) return;

        int avail = remainingNet.getOrDefault(commodityId, 0);
        if (avail <= 0) return;

        int moved = Math.min(amount, avail);
        internalSent.merge(commodityId, moved, Integer::sum);

        int newVal = avail - moved;
        if (newVal == 0) remainingNet.remove(commodityId);
        else remainingNet.put(commodityId, newVal);
    }

    public void addInternalReceived(String commodityId, int amount) {
        if (amount <= 0) return;

        int needSigned = remainingNet.getOrDefault(commodityId, 0);
        if (needSigned >= 0) return;

        int need = -needSigned;
        int moved = Math.min(amount, need);
        internalReceived.merge(commodityId, moved, Integer::sum);

        int newNeed = need - moved;
        if (newNeed == 0) remainingNet.remove(commodityId);
        else remainingNet.put(commodityId, -newNeed);
    }
}
