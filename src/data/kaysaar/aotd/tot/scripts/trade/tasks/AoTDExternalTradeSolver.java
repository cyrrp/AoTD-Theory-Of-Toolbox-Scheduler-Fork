// file: data/kaysaar/aotd/tot/scripts/trade/tasks/AoTDExternalTradeSolver.java
package data.kaysaar.aotd.tot.scripts.trade.tasks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.kaysaar.aotd.tot.scripts.economy.AoTDSectorProductionDemandDataUtils;
import data.kaysaar.aotd.tot.scripts.trade.AoTDSectorExternalIndex;
import data.kaysaar.aotd.tot.scripts.trade.ScavengerGuildUtils;
import data.kaysaar.aotd.tot.scripts.trade.SectorSurplusConsumptionStats;
import data.kaysaar.aotd.tot.scripts.trade.SurplusConsumptionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class AoTDExternalTradeSolver {

    /**
     * Scavenger Guild mechanic:
     * If sector demand exceeds sector production by more than threshold,
     * inject enough synthetic supply so that remaining shortage is capped to threshold*production.
     */
    private void applyScavengerGuildIfNeeded(
            AoTDSectorExternalIndex idx,
            String commodityId,
            ArrayList<AoTDSectorExternalIndex.Offer> exporters,
            ArrayList<AoTDSectorExternalIndex.Offer> importers
    ) {
        if (importers == null || importers.isEmpty()) return;

        int extra = ScavengerGuildUtils.getCoveredAmountFromSector(commodityId);
        if (extra <= 0) return;

        if (exporters == null) exporters = new ArrayList<>();

        float maxW = 0f;
        for (AoTDSectorExternalIndex.Offer e : exporters) maxW = Math.max(maxW, e.weight);
        for (AoTDSectorExternalIndex.Offer i : importers) maxW = Math.max(maxW, i.weight);
        if (maxW <= 0f) maxW = 100f;

        float scavWeight = maxW * 1.25f + 1f;

        AoTDSectorExternalIndex.Offer scav = idx.createScavengerOffer(commodityId, extra, scavWeight);
        exporters.add(scav);

        idx.exportersByCommodity.put(commodityId, exporters);
    }

    /**
     * Sector Surplus Cap (AFTER matching):
     * For covered commodities, reduce leftover (unmatched) exporter supply so that:
     *
     *   sectorSupply <= sectorDemand * (1 + cap)
     *
     * This is done ONLY on remaining exporter offer amounts after matching,
     * so it cannot create shortages when the sector can satisfy them.
     *
     * It records:
     *  - per faction totals in SectorSurplusConsumptionStats
     *  - per market amount removed in AoTDMarketData.externalExcessExported
     */
    private void applySurplusCapAfterMatching(
            String commodityId,
            ArrayList<AoTDSectorExternalIndex.Offer> exporters
    ) {
        if (!SurplusConsumptionUtils.doesCoverCommodity(commodityId)) return;
        if (exporters == null || exporters.isEmpty()) return;

        // leftover supply after matching = sum of remaining exporter offer amounts
        long leftoverSupply = 0;
        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (e == null) continue;
            if (e.isScavenger) continue; // don't cap synthetic supply
            if (e.amount > 0) leftoverSupply += e.amount;
        }
        if (leftoverSupply <= 0) return;

        int sectorSupply = AoTDSectorProductionDemandDataUtils.getTotalProductionFromSector(commodityId);
        int sectorDemand = AoTDSectorProductionDemandDataUtils.getTotalDemandFromSector(commodityId);
        if (sectorSupply <= 0 || sectorDemand < 0) return;

        float cap = SurplusConsumptionUtils.getCapPercent(commodityId); // default 0.10f

        // allowed TOTAL supply = demand * (1 + cap)
        // => allowed net surplus = allowedSupply - demand = demand*cap
        long allowedNetSurplus = (long) Math.ceil(sectorDemand * (double) cap);

        // if sector is not actually beyond cap globally, do nothing
        long actualNetSurplus = (long) sectorSupply - (long) sectorDemand;
        if (actualNetSurplus <= allowedNetSurplus) return;

        // BUT we only can reduce what is leftover after matching (unmatched exports).
        // amount to remove = min(leftoverSupply, actualNetSurplus - allowedNetSurplus)
        long toRemove = Math.min(leftoverSupply, actualNetSurplus - allowedNetSurplus);
        if (toRemove <= 0) return;

        // Deterministic: remove from the highest-weight exporters first.
        // Stable market-id tie breaking prevents load/insertion order from changing
        // which producer absorbs the surplus cap when weights are equal.
        exporters.sort(AoTDExternalTradeSolver::compareSurplusPriority);

        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (toRemove <= 0) break;
            if (e == null) continue;
            if (e.isScavenger) continue;
            if (e.amount <= 0) continue;

            int removed = (int) Math.min((long) e.amount, toRemove);
            if (removed <= 0) continue;

            // reduce leftover export offer
            e.amount -= removed;

            // reduce remainingNet as well (less export surplus remains)
            e.data.remainingNet.merge(commodityId, -removed, Integer::sum);
            if (e.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                e.data.remainingNet.remove(commodityId);
            }

            // record per-faction stats
            if (e.market != null) {
                SectorSurplusConsumptionStats.getInstance()
                        .record(commodityId, e.market.getFactionId(), removed);
            }

            // record per-market "excess exported" removed by cap
            e.data.externalExcessExported.merge(commodityId, removed, Integer::sum);

            toRemove -= removed;
        }
    }

    /**
     * Runs month-end matching.
     */
    public void runMonthEndExternalTrade(AoTDSectorExternalIndex idx) {
        // A stable global order makes diagnostics and any cross-commodity side effects
        // reproducible as well. Each commodity also has its own independent PRNG seed.
        Set<String> commodities = new TreeSet<>();
        commodities.addAll(idx.exportersByCommodity.keySet());
        commodities.addAll(idx.importersByCommodity.keySet());

        for (String commodityId : commodities) {
            runForCommodity(idx, commodityId);
        }
    }

    private void runForCommodity(AoTDSectorExternalIndex idx, String commodityId) {
        ArrayList<AoTDSectorExternalIndex.Offer> exporters = idx.exportersByCommodity.get(commodityId);
        ArrayList<AoTDSectorExternalIndex.Offer> importers = idx.importersByCommodity.get(commodityId);

        // If nobody needs it, nothing to match.
        // (Keep this early return: scavengers and matching only matter if there is demand.)
        if (importers == null || importers.isEmpty()) return;

        // allow scavengers even if exporters == null/empty
        applyScavengerGuildIfNeeded(idx, commodityId, exporters, importers);

        exporters = idx.exportersByCommodity.get(commodityId);
        if (exporters == null || exporters.isEmpty()) return;

        // WeightedRandomPicker is order-sensitive even with a fixed PRNG. Canonicalize
        // offer order first, then use one commodity-local deterministic random stream.
        exporters.sort(Comparator.comparing(AoTDExternalTradeSolver::stableOfferId));
        importers.sort(Comparator.comparing(AoTDExternalTradeSolver::stableOfferId));
        Random random = new Random(computeExternalTradeSeed(commodityId));
        WeightedRandomPicker<AoTDSectorExternalIndex.Offer> expPicker = new WeightedRandomPicker<>(random);
        WeightedRandomPicker<AoTDSectorExternalIndex.Offer> impPicker = new WeightedRandomPicker<>(random);

        for (AoTDSectorExternalIndex.Offer e : exporters) {
            if (e != null && e.amount > 0) expPicker.add(e, e.weight);
        }
        for (AoTDSectorExternalIndex.Offer i : importers) {
            if (i != null && i.amount > 0) impPicker.add(i, i.weight);
        }

        int guard = 0;
        while (!expPicker.isEmpty() && !impPicker.isEmpty()) {
            if (++guard > 200000) break;

            AoTDSectorExternalIndex.Offer imp = impPicker.pick();
            AoTDSectorExternalIndex.Offer exp = expPicker.pick();
            if (imp == null || exp == null) break;

            if (imp.amount <= 0) { impPicker.remove(imp); continue; }
            if (exp.amount <= 0) { expPicker.remove(exp); continue; }

            int moved = Math.min(exp.amount, imp.amount);

            // exporter moves toward 0
            exp.data.remainingNet.merge(commodityId, -moved, Integer::sum);
            if (exp.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                exp.data.remainingNet.remove(commodityId);
            }

            // importer moves toward 0
            imp.data.remainingNet.merge(commodityId, moved, Integer::sum);
            if (imp.data.remainingNet.getOrDefault(commodityId, 0) == 0) {
                imp.data.remainingNet.remove(commodityId);
            }

            exp.amount -= moved;
            exp.data.addSoldOutside(commodityId, moved);
            imp.amount -= moved;

            if (exp.amount <= 0) expPicker.remove(exp);
            if (imp.amount <= 0) impPicker.remove(imp);
        }

        // IMPORTANT: surplus cap happens AFTER matching, and only touches leftover supply.
        applySurplusCapAfterMatching(commodityId, exporters);
    }

    /**
     * Stable identity used for canonical ordering. A normal market id is unique for
     * one commodity. Synthetic scavenger supply receives its own fixed identity.
     */
    private static int compareSurplusPriority(
            AoTDSectorExternalIndex.Offer left,
            AoTDSectorExternalIndex.Offer right) {
        if (left == right) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        int weight = Float.compare(right.weight, left.weight);
        if (weight != 0) return weight;
        return stableOfferId(left).compareTo(stableOfferId(right));
    }

    private static String stableOfferId(AoTDSectorExternalIndex.Offer offer) {
        if (offer == null) return "\uffff:null";
        if (offer.isScavenger) return "\u0000:scavenger";
        if (offer.market != null && offer.market.getId() != null) {
            return offer.market.getId();
        }
        if (offer.data != null && offer.data.marketId != null) {
            return offer.data.marketId;
        }
        return "\uffff:unknown";
    }

    /**
     * Seed contract: campaign seed + economy month-cycle + commodity id.
     * The month-cycle is cycle*12+month, so each monthly settlement has a unique,
     * replayable seed while remaining independent of thread and collection order.
     */
    private static long computeExternalTradeSeed(String commodityId) {
        String campaignSeed = "";
        long economyCycle = 0L;
        if (Global.getSector() != null) {
            String configuredSeed = Global.getSector().getSeedString();
            if (configuredSeed != null) campaignSeed = configuredSeed;
            CampaignClockAPI clock = Global.getSector().getClock();
            if (clock != null) {
                economyCycle = ((long) clock.getCycle() * 12L) + clock.getMonth();
            }
        }

        long hash = 0xcbf29ce484222325L;
        hash = mixString(hash, campaignSeed);
        hash = mixLong(hash, economyCycle);
        hash = mixString(hash, commodityId == null ? "" : commodityId);
        return avalanche(hash);
    }

    private static long mixString(long hash, String value) {
        hash ^= 0xffL;
        hash *= 0x100000001b3L;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            hash ^= c & 0xffL;
            hash *= 0x100000001b3L;
            hash ^= (c >>> 8) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mixLong(long hash, long value) {
        hash ^= 0xfeL;
        hash *= 0x100000001b3L;
        for (int shift = 0; shift < 64; shift += 8) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long avalanche(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
