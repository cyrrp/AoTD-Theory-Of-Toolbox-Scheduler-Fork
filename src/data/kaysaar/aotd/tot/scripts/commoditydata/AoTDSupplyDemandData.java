package data.kaysaar.aotd.tot.scripts.commoditydata;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpec;
import data.kaysaar.aotd.tot.plugins.AoTDCommodityEconSpecManager;
import data.kaysaar.aotd.tot.scripts.economy.AoTDIndustryData;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomySemanticBaseline;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

import java.util.LinkedHashMap;

/**
 * Authoritative supply/demand state for one market commodity.
 *
 * <p>Stage 8.2 uses a two-phase refresh. The complete next revision is first
 * calculated into reusable staging maps. Only after every industry calculation
 * succeeds are the map references and aggregate values published. A failed
 * refresh therefore leaves the previous committed revision untouched.</p>
 */
public class AoTDSupplyDemandData {
    public volatile LinkedHashMap<String, MutableStat> demandUnitsFromIndustries =
            new LinkedHashMap<>();
    public volatile LinkedHashMap<String, MutableStat> supplyUnitsFromIndustries =
            new LinkedHashMap<>();
    public String commodityID;

    public AoTDSupplyDemandData(String commodityID) {
        this.commodityID = commodityID;
    }

    public transient AoTDCommodityEconSpec ecSpec;
    public volatile int supply, demand, available;
    public MutableStatWithTempMods additionalProduction = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalDemand = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalImport = new MutableStatWithTempMods(0f);
    public MutableStatWithTempMods additionalExport = new MutableStatWithTempMods(0f);

    private transient long authoritativeDirtyGeneration = Long.MIN_VALUE;
    private transient LinkedHashMap<String, MutableStat> stagingDemandUnitsFromIndustries =
            new LinkedHashMap<>();
    private transient LinkedHashMap<String, MutableStat> stagingSupplyUnitsFromIndustries =
            new LinkedHashMap<>();

    public AoTDCommodityEconSpec getEconSpec() {
        if (ecSpec == null) {
            ecSpec = AoTDCommodityEconSpecManager.getEconSpec(commodityID);
        }
        return ecSpec;
    }

    public int getExport(CommodityOnMarketAPI commodity) {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public int getImportsExcludingDeficits() {
        return -getExportExcludingDeficit();
    }

    public int getExportExcludingDeficit() {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    public boolean doesHaveSupplyOrDemand() {
        int sup = getTotalRawUnitsFromSupply();
        int dem = getTotalRawUnitsFromDemand();
        return dem != 0 || sup != 0;
    }

    public void updateSupplyDemandData(MarketAPI market) {
        updateSupplyDemandData(market, false);
    }

    /**
     * Compatibility entry point used outside the staged market-price pipeline.
     * The refresh is still atomic for this commodity. Failures are logged and
     * the previous committed revision is preserved.
     */
    public void updateSupplyDemandData(MarketAPI market, boolean force) {
        try {
            PreparedRefresh prepared = prepareSupplyDemandData(market, force);
            if (commitPreparedRefresh(prepared)) {
                finishPreparedRefresh(prepared);
            }
        } catch (RuntimeException failure) {
            AoTDEconomySemanticBaseline.operation("supply-demand.refresh-failure", market);
            Global.getLogger(AoTDSupplyDemandData.class).error(
                    "AoTD supply/demand refresh failed for commodity " + commodityID
                            + " on market " + (market == null ? "<null>" : market.getId())
                            + "; preserving the previous committed revision.", failure);
        }
    }

    /**
     * Calculates a complete next revision without changing authoritative state.
     * The returned object is owned by this instance and must be committed or
     * discarded before preparing another revision for the same commodity.
     */
    public synchronized PreparedRefresh prepareSupplyDemandData(
            MarketAPI market, boolean force) {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        long targetGeneration = MarketRegistry.getMarketDirtyGeneration(market);
        if (!force && targetGeneration > 0L
                && targetGeneration == authoritativeDirtyGeneration) {
            AoTDEconomySemanticBaseline.operation("supply-demand.skipped-current", market);
            return PreparedRefresh.skipped(this, targetGeneration);
        }

        ensureStagingMaps();
        stagingDemandUnitsFromIndustries.clear();
        stagingSupplyUnitsFromIndustries.clear();

        int nextSupply = 0;
        int nextDemand = 0;
        int pairs = 0;
        try (AoTDEconomySemanticBaseline.Scope scope =
                     AoTDEconomySemanticBaseline.begin(
                             "supply-demand.prepare", market, commodityID)) {
            try {
                for (Industry industry : market.getIndustries()) {
                    String industryId = industry.getId();
                    MutableStat demandStat = industry.getDemand(commodityID).getQuantity();
                    MutableStat supplyStat = industry.getSupply(commodityID).getQuantity();

                    stagingDemandUnitsFromIndustries.put(industryId, demandStat);
                    stagingSupplyUnitsFromIndustries.put(industryId, supplyStat);

                    nextSupply += getEconSpec().getCalculationScript()
                            .getRawUnitsFromSupply(
                                    supplyStat, null, commodityID, industry);
                    nextDemand += getEconSpec().getCalculationScript()
                            .getRawUnitsFromDemand(
                                    demandStat, null, commodityID, industry);
                    pairs++;
                }
            } catch (RuntimeException failure) {
                scope.failed();
                stagingDemandUnitsFromIndustries.clear();
                stagingSupplyUnitsFromIndustries.clear();
                throw new SupplyDemandRefreshException(
                        market.getId(), commodityID, failure);
            }
        }

        return new PreparedRefresh(
                this, market, targetGeneration, nextSupply, nextDemand, pairs,
                stagingDemandUnitsFromIndustries,
                stagingSupplyUnitsFromIndustries,
                false);
    }

    /**
     * Publishes a previously prepared revision using reference swaps. The method
     * performs no industry calculations and has no partial-read failure path.
     */
    public synchronized boolean commitPreparedRefresh(PreparedRefresh prepared) {
        if (prepared == null || prepared.owner != this) {
            throw new IllegalArgumentException("prepared refresh belongs to another owner");
        }
        if (prepared.skipped) return false;
        if (prepared.demandUnits != stagingDemandUnitsFromIndustries
                || prepared.supplyUnits != stagingSupplyUnitsFromIndustries) {
            throw new IllegalStateException("prepared refresh is no longer current");
        }

        LinkedHashMap<String, MutableStat> oldDemand = demandUnitsFromIndustries;
        LinkedHashMap<String, MutableStat> oldSupply = supplyUnitsFromIndustries;

        demandUnitsFromIndustries = prepared.demandUnits;
        supplyUnitsFromIndustries = prepared.supplyUnits;
        supply = prepared.nextSupply;
        demand = prepared.nextDemand;
        if (prepared.targetGeneration > 0L) {
            authoritativeDirtyGeneration = prepared.targetGeneration;
        }

        // Reuse the formerly authoritative maps as the next staging buffers.
        stagingDemandUnitsFromIndustries = oldDemand == null
                ? new LinkedHashMap<String, MutableStat>() : oldDemand;
        stagingSupplyUnitsFromIndustries = oldSupply == null
                ? new LinkedHashMap<String, MutableStat>() : oldSupply;

        AoTDEconomySemanticBaseline.operation("supply-demand.atomic-commit", prepared.market);
        return true;
    }

    /**
     * Runs auxiliary bookkeeping only after the complete authoritative revision
     * has been published. This is deliberately separate from the reference swap
     * so a market-wide caller can commit every commodity before any callback.
     */
    public void finishPreparedRefresh(PreparedRefresh prepared) {
        if (prepared == null || prepared.owner != this || prepared.skipped) return;
        AoTDEconomySemanticBaseline.operation(
                "supply-demand.industry-pairs", prepared.industryPairs);
        if (supply != 0 || demand != 0) {
            try {
                AoTDTradeManager.getInstance().recordPossibleCommodity(commodityID);
            } catch (RuntimeException failure) {
                // Recording the global candidate set is auxiliary. The local
                // authoritative revision remains valid and must not be rolled back.
                Global.getLogger(AoTDSupplyDemandData.class).warn(
                        "Unable to record AoTD commodity candidate " + commodityID,
                        failure);
            }
        }
    }

    /** Releases prepared staging data without changing authoritative state. */
    public synchronized void discardPreparedRefresh(PreparedRefresh prepared) {
        if (prepared == null || prepared.owner != this || prepared.skipped) return;
        if (prepared.demandUnits == stagingDemandUnitsFromIndustries) {
            stagingDemandUnitsFromIndustries.clear();
        }
        if (prepared.supplyUnits == stagingSupplyUnitsFromIndustries) {
            stagingSupplyUnitsFromIndustries.clear();
        }
    }

    private void ensureStagingMaps() {
        if (stagingDemandUnitsFromIndustries == null) {
            stagingDemandUnitsFromIndustries = new LinkedHashMap<>();
        }
        if (stagingSupplyUnitsFromIndustries == null) {
            stagingSupplyUnitsFromIndustries = new LinkedHashMap<>();
        }
        if (demandUnitsFromIndustries == null) {
            demandUnitsFromIndustries = new LinkedHashMap<>();
        }
        if (supplyUnitsFromIndustries == null) {
            supplyUnitsFromIndustries = new LinkedHashMap<>();
        }
    }

    public int getDemandExceptPendingIndustries(MarketAPI market) {
        int total = 0;
        for (Industry s : market.getIndustries()) {
            if (!AoTDIndustryData.getInstance(market).isPending(s.getId())) {
                total += getEconSpec().getCalculationScript().getRawUnitsFromDemand(
                        s.getDemand(commodityID).getQuantity(), null, commodityID, s);
            }
        }
        return total;
    }

    public int getRawDemandFromIndustry(Industry industry) {
        return getEconSpec().getCalculationScript().getRawUnitsFromDemand(
                industry.getDemand(commodityID).getQuantity(), null, commodityID, industry);
    }

    public int getRawSupplyFromIndustry(Industry industry) {
        if (industry.isDisrupted()) return 0;
        return getEconSpec().getCalculationScript().getRawUnitsFromSupply(
                industry.getSupply(commodityID).getQuantity(), null, commodityID, industry);
    }

    public LinkedHashMap<String, MutableStat> getDemandUnitsFromIndustries() {
        return demandUnitsFromIndustries;
    }

    public LinkedHashMap<String, MutableStat> getSupplyUnitsFromIndustries() {
        return supplyUnitsFromIndustries;
    }

    public int getTotalRawUnitsFromSupply() {
        return supply;
    }

    public int getTotalRawUnitsFromDemand() {
        return demand;
    }

    public int getRawNetExport() {
        return getTotalRawUnitsFromSupply() - getTotalRawUnitsFromDemand();
    }

    /**
     * Computes the current trade input directly from live industry stats without
     * changing the authoritative supply/demand revision. This is used after the
     * vanilla immigration phase, where a size increase may have reapplied
     * industries after the normal local materialization pass.
     */
    public int computeRawNetForTradeSnapshot(MarketAPI market) {
        if (market == null) {
            throw new IllegalArgumentException("market must not be null");
        }
        int currentSupply = 0;
        int currentDemand = 0;
        try {
            for (Industry industry : market.getIndustries()) {
                currentSupply += getEconSpec().getCalculationScript()
                        .getRawUnitsFromSupply(
                                industry.getSupply(commodityID).getQuantity(),
                                null, commodityID, industry);
                currentDemand += getEconSpec().getCalculationScript()
                        .getRawUnitsFromDemand(
                                industry.getDemand(commodityID).getQuantity(),
                                null, commodityID, industry);
            }
        } catch (RuntimeException failure) {
            throw new SupplyDemandRefreshException(
                    market.getId(), commodityID, failure);
        }
        return currentSupply - currentDemand;
    }

    public int getTotalExportTowardsOtherSources() {
        return additionalExport.getModifiedInt();
    }

    public int getTotalImportFromOtherSources() {
        return additionalImport.getModifiedInt();
    }

    public MutableStatWithTempMods getAdditionalDemand() {
        return additionalDemand;
    }

    public MutableStatWithTempMods getAdditionalExport() {
        return additionalExport;
    }

    public MutableStatWithTempMods getAdditionalImport() {
        return additionalImport;
    }

    public MutableStatWithTempMods getAdditionalProduction() {
        return additionalProduction;
    }

    public void advance(float days) {
        additionalDemand.advance(days);
        additionalImport.advance(days);
        additionalExport.advance(days);
        additionalProduction.advance(days);
    }

    public int getAvailableOnThisMarket(
            float cargo, MarketAPI market, String commodityId) {
        int available = 0;
        float remainingCargo = cargo;
        AoTDIndustryData industryData = AoTDIndustryData.getInstance(market);

        for (Industry industry : industryData.getStableIndustryOrder(market)) {
            if (industryData.isPending(industry.getId())) continue;
            if (remainingCargo < 1f) break;

            float raw = getEconSpec().getCalculationScript().getRawUnitsFromDemand(
                    industry.getDemand(commodityId).getQuantity(),
                    market, commodityId, industry);
            if (raw > remainingCargo) {
                float filled = remainingCargo / raw;
                int rem = Math.round(
                        filled * industry.getDemand(commodityId)
                                .getQuantity().getModifiedInt());
                available += rem;
                break;
            }

            remainingCargo -= raw;
            available += industry.getDemand(commodityId)
                    .getQuantity().getModifiedInt();
        }
        return available;
    }

    public static final class PreparedRefresh {
        private final AoTDSupplyDemandData owner;
        private final MarketAPI market;
        private final long targetGeneration;
        private final int nextSupply;
        private final int nextDemand;
        private final int industryPairs;
        private final LinkedHashMap<String, MutableStat> demandUnits;
        private final LinkedHashMap<String, MutableStat> supplyUnits;
        private final boolean skipped;

        private PreparedRefresh(
                AoTDSupplyDemandData owner,
                MarketAPI market,
                long targetGeneration,
                int nextSupply,
                int nextDemand,
                int industryPairs,
                LinkedHashMap<String, MutableStat> demandUnits,
                LinkedHashMap<String, MutableStat> supplyUnits,
                boolean skipped) {
            this.owner = owner;
            this.market = market;
            this.targetGeneration = targetGeneration;
            this.nextSupply = nextSupply;
            this.nextDemand = nextDemand;
            this.industryPairs = industryPairs;
            this.demandUnits = demandUnits;
            this.supplyUnits = supplyUnits;
            this.skipped = skipped;
        }

        private static PreparedRefresh skipped(
                AoTDSupplyDemandData owner, long generation) {
            return new PreparedRefresh(
                    owner, null, generation, owner.supply, owner.demand, 0,
                    null, null, true);
        }

        public boolean isSkipped() {
            return skipped;
        }
    }

    public static final class SupplyDemandRefreshException extends RuntimeException {
        public final String marketId;
        public final String commodityId;

        SupplyDemandRefreshException(
                String marketId, String commodityId, Throwable cause) {
            super("Unable to prepare supply/demand for market=" + marketId
                    + ", commodity=" + commodityId, cause);
            this.marketId = marketId;
            this.commodityId = commodityId;
        }
    }
}
