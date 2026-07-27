package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;

/**
 * Owner-local revision gate for synchronous UI economy refreshes.
 *
 * <p>The coordinator deliberately stores only a market id and primitive
 * revisions. It never becomes a process-lifetime root for a campaign market.
 * A repeated Cargo/market UI request is skipped only when the exact runtime,
 * registry and market revisions are unchanged and the market has no pending
 * derived work.</p>
 */
final class AoTDUIEconomyRefreshCoordinator {
    private long completedCampaignEpoch;
    private long completedEconomyEpoch;
    private long completedRegistryGeneration;
    private long completedDirtyGeneration;
    private long completedMarketDirtyGeneration;
    private int completedMarketIdentityHash;
    private String completedMarketId;

    private long completedRefreshes;
    private long skippedRefreshes;
    private long invalidations;

    MarketAPI consumeOpeningMarket() {
        Object candidate = SchedulerBridge.consumeOpeningMarket();
        return candidate instanceof MarketAPI ? (MarketAPI) candidate : null;
    }

    boolean isCurrent(MarketAPI market) {
        if (market == null || completedMarketId == null) return false;
        if (completedCampaignEpoch != AoTDRuntimeEpoch.getCampaignEpoch()
                || completedEconomyEpoch != AoTDRuntimeEpoch.getEconomyEpoch()) {
            return false;
        }
        String id = market.getId();
        if (id == null || !id.equals(completedMarketId)
                || completedMarketIdentityHash != System.identityHashCode(market)) {
            return false;
        }
        if (MarketRegistry.getRegistryGeneration() != completedRegistryGeneration
                || MarketRegistry.getDirtyGeneration() != completedDirtyGeneration
                || MarketRegistry.getMarketDirtyGeneration(market)
                != completedMarketDirtyGeneration) {
            return false;
        }
        if (MarketRegistry.lookupMarket(id) != market) return false;
        return !MarketRegistry.needsDerivedRefresh(market);
    }

    void recordCompleted(MarketAPI market) {
        if (market == null || market.getId() == null
                || MarketRegistry.needsDerivedRefresh(market)) {
            invalidate("incomplete-market-refresh");
            return;
        }
        completedCampaignEpoch = AoTDRuntimeEpoch.getCampaignEpoch();
        completedEconomyEpoch = AoTDRuntimeEpoch.getEconomyEpoch();
        completedRegistryGeneration = MarketRegistry.getRegistryGeneration();
        completedDirtyGeneration = MarketRegistry.getDirtyGeneration();
        completedMarketDirtyGeneration = MarketRegistry.getMarketDirtyGeneration(market);
        completedMarketIdentityHash = System.identityHashCode(market);
        completedMarketId = market.getId();
        completedRefreshes++;
    }

    void recordSkip() {
        skippedRefreshes++;
    }

    void invalidate(String reason) {
        completedCampaignEpoch = 0L;
        completedEconomyEpoch = 0L;
        completedRegistryGeneration = 0L;
        completedDirtyGeneration = 0L;
        completedMarketDirtyGeneration = 0L;
        completedMarketIdentityHash = 0;
        completedMarketId = null;
        invalidations++;
        AoTDEconomySemanticBaseline.operation("ui-economy.refresh-invalidated", 1L);
    }

    String statusSummary() {
        return "completed=" + completedRefreshes
                + ", skipped=" + skippedRefreshes
                + ", invalidations=" + invalidations
                + ", market=" + completedMarketId
                + ", campaignEpoch=" + completedCampaignEpoch
                + ", economyEpoch=" + completedEconomyEpoch
                + ", registryGeneration=" + completedRegistryGeneration
                + ", dirtyGeneration=" + completedDirtyGeneration
                + ", marketDirtyGeneration=" + completedMarketDirtyGeneration;
    }
}
