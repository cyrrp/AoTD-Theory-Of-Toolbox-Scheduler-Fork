package data.kaysaar.aotd.tot.listeners;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Market;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTdMainWorkTask2;
import data.kaysaar.aotd.tot.scripts.submarket.aotd.AoTDOpenMarketPlugin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Candidate cache for the F1 trade table replacement.
 *
 * <p>Important: this cache intentionally does NOT cache final prices. Final buy/sell prices are
 * recalculated live by {@link AoTDPriceTableRemoval}, because prices can change due to player trade
 * impact, excess/deficit movement, tariffs, and other temporary modifiers.
 *
 * <p>What is cached here is only the expensive broad-market scan: which markets are plausible
 * buy/sell candidates for each commodity, plus rough stable values useful for pre-sorting. This
 * removes the tooltip hitch without making the displayed price stale.
 */
public final class AoTDTradePriceCache {

    private AoTDTradePriceCache() {}

    /**
     * Large enough that live sorting still has good candidates, small enough that opening the
     * tooltip does not call getSupplyPrice()/getDemandPrice() for every market in the sector.
     */
    private static final int MAX_CANDIDATES_PER_SIDE = 48;

    private static final Map<String, CachedCommodityCandidates> CACHE = new HashMap<>();

    public static synchronized CandidateSet getCandidates(String commodityId) {
        if (commodityId == null) {
            return CandidateSet.EMPTY;
        }

        EconomyAPI economy = Global.getSector() == null ? null : Global.getSector().getEconomy();
        if (economy == null) {
            return CandidateSet.EMPTY;
        }

        int cycle = Global.getSector().getClock().getCycle();
        int month = Global.getSector().getClock().getMonth();
        int marketCount = economy.getMarketsCopy().size();

        CachedCommodityCandidates cached = CACHE.get(commodityId);
        if (cached != null && !cached.isStale(cycle, month, marketCount)) {
            return cached.candidates;
        }

        CachedCommodityCandidates rebuilt = rebuild(commodityId, cycle, month, marketCount);
        CACHE.put(commodityId, rebuilt);
        return rebuilt.candidates;
    }

    public static synchronized void invalidateCommodity(String commodityId) {
        if (commodityId != null) {
            CACHE.remove(commodityId);
        }
    }

    public static synchronized void invalidateAll() {
        CACHE.clear();
    }

    private static CachedCommodityCandidates rebuild(
            String commodityId, int cycle, int month, int marketCount) {
        ArrayList<Candidate> sellCandidates = new ArrayList<>();
        ArrayList<Candidate> buyCandidates = new ArrayList<>();

        EconomyAPI economy = Global.getSector().getEconomy();
        for (MarketAPI marketAPI : economy.getMarketsCopy()) {
            if (marketAPI == null || marketAPI.isHidden()) continue;
            if (!(marketAPI instanceof Market)) continue;

            AoTDCommodityOnMarket com =
                    AoTDCommodityOnMarket.getComMarketInstanceSave(marketAPI, commodityId);
            if (com == null) continue;

            int demand = safeDemand(com);
            int supply = safeSupply(com);
            int currentDeficit = Math.max(0, com.getDeficitQuantity());
            int anchorDeficit = Math.max(0, com.getDef());
            int currentExcess = Math.max(0, com.getExcessQuantity());
            int anchorExcess = Math.max(0, com.getExc());
            int stockpileDisplay = Math.max(0, AoTDOpenMarketPlugin.getStockPileToolbox(com));

            int stableAvailable = 0;
            try {
                stableAvailable =
                        Math.max(
                                0,
                                (int)
                                        AoTdMainWorkTask2.getAoTDStableSharedSubmarketLimit(
                                                (Market) marketAPI, com, supply));
            } catch (Throwable ignored) {
                stableAvailable = stockpileDisplay;
            }

            Candidate candidate =
                    new Candidate(
                            marketAPI.getId(),
                            demand,
                            supply,
                            currentDeficit,
                            anchorDeficit,
                            currentExcess,
                            anchorExcess,
                            stableAvailable,
                            stockpileDisplay);

            if (demand > 0) {
                sellCandidates.add(candidate);
            }

            // Do not cache markets that currently display zero buyable stock. The UI still
            // revalidates
            // this live, because stock can change after the cache is built.
            if (stableAvailable > 0 && stockpileDisplay > 0) {
                buyCandidates.add(candidate);
            }
        }

        sellCandidates.sort(
                new Comparator<Candidate>() {
                    @Override
                    public int compare(Candidate a, Candidate b) {
                        int deficitCompare =
                                Integer.compare(
                                        b.getSellPressureSortValue(), a.getSellPressureSortValue());
                        if (deficitCompare != 0) return deficitCompare;

                        int demandCompare = Integer.compare(b.demand, a.demand);
                        if (demandCompare != 0) return demandCompare;

                        return Integer.compare(b.stableAvailable, a.stableAvailable);
                    }
                });

        buyCandidates.sort(
                new Comparator<Candidate>() {
                    @Override
                    public int compare(Candidate a, Candidate b) {
                        int excessCompare =
                                Integer.compare(
                                        b.getBuyPressureSortValue(), a.getBuyPressureSortValue());
                        if (excessCompare != 0) return excessCompare;

                        int availableCompare =
                                Integer.compare(
                                        b.getEffectiveAvailableSortValue(),
                                        a.getEffectiveAvailableSortValue());
                        if (availableCompare != 0) return availableCompare;

                        return Integer.compare(b.stockpileDisplay, a.stockpileDisplay);
                    }
                });

        CandidateSet set =
                new CandidateSet(
                        trim(sellCandidates, MAX_CANDIDATES_PER_SIDE),
                        trim(buyCandidates, MAX_CANDIDATES_PER_SIDE));

        return new CachedCommodityCandidates(cycle, month, marketCount, set);
    }

    private static int safeDemand(AoTDCommodityOnMarket com) {
        try {
            return Math.max(0, com.getSupplyDemandData().getTotalRawUnitsFromDemand());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int safeSupply(AoTDCommodityOnMarket com) {
        try {
            return Math.max(0, com.getSupplyDemandData().getTotalRawUnitsFromSupply());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static List<Candidate> trim(ArrayList<Candidate> rows, int max) {
        int count = Math.min(max, rows.size());
        ArrayList<Candidate> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(rows.get(i));
        }
        return out;
    }

    private static class CachedCommodityCandidates {
        final int cycle;
        final int month;
        final int marketCount;
        final CandidateSet candidates;

        CachedCommodityCandidates(int cycle, int month, int marketCount, CandidateSet candidates) {
            this.cycle = cycle;
            this.month = month;
            this.marketCount = marketCount;
            this.candidates = candidates;
        }

        boolean isStale(int cycle, int month, int marketCount) {
            return this.cycle != cycle || this.month != month || this.marketCount != marketCount;
        }
    }

    public static class CandidateSet {
        static final CandidateSet EMPTY =
                new CandidateSet(new ArrayList<Candidate>(), new ArrayList<Candidate>());

        public final List<Candidate> sellCandidates;
        public final List<Candidate> buyCandidates;

        CandidateSet(List<Candidate> sellCandidates, List<Candidate> buyCandidates) {
            this.sellCandidates = sellCandidates;
            this.buyCandidates = buyCandidates;
        }
    }

    public static class Candidate {
        public final String marketId;
        public final int demand;
        public final int supply;
        public final int currentDeficit;
        public final int anchorDeficit;
        public final int currentExcess;
        public final int anchorExcess;
        public final int stableAvailable;
        public final int stockpileDisplay;

        Candidate(
                String marketId,
                int demand,
                int supply,
                int currentDeficit,
                int anchorDeficit,
                int currentExcess,
                int anchorExcess,
                int stableAvailable,
                int stockpileDisplay) {
            this.marketId = marketId;
            this.demand = demand;
            this.supply = supply;
            this.currentDeficit = currentDeficit;
            this.anchorDeficit = anchorDeficit;
            this.currentExcess = currentExcess;
            this.anchorExcess = anchorExcess;
            this.stableAvailable = stableAvailable;
            this.stockpileDisplay = stockpileDisplay;
        }

        public MarketAPI getMarket() {
            EconomyAPI economy =
                    Global.getSector() == null ? null : Global.getSector().getEconomy();
            if (economy == null || marketId == null) return null;
            return economy.getMarket(marketId);
        }

        int getSellPressureSortValue() {
            return Math.max(currentDeficit, anchorDeficit);
        }

        int getBuyPressureSortValue() {
            return Math.max(currentExcess, anchorExcess);
        }

        int getEffectiveAvailableSortValue() {
            return Math.max(0, Math.min(stableAvailable, stockpileDisplay));
        }
    }
}
