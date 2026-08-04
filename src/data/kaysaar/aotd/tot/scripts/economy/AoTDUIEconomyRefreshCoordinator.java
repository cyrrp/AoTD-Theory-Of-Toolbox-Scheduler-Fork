package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.compat.MarketRegistry;

/**
 * Owner-local revision gate for synchronous UI economy refreshes.
 *
 * <p>The coordinator deliberately stores only a market id and primitive revisions. It never becomes
 * a process-lifetime root for a campaign market. A repeated Cargo/market UI request is skipped only
 * when the exact runtime, registry and market revisions are unchanged and the market has no pending
 * derived work.
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
    private long syntheticCargoSkips;
    private long conditionOnlySkips;
    private long invalidations;
    private long publicationFailures;

    boolean isCurrent(MarketAPI market) {
        try {
            if (market == null || completedMarketId == null) return false;
            if (completedCampaignEpoch != AoTDRuntimeEpoch.getCampaignEpoch()
                    || completedEconomyEpoch != AoTDRuntimeEpoch.getEconomyEpoch()) {
                return false;
            }
            String id = market.getId();
            if (id == null
                    || !id.equals(completedMarketId)
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
        } catch (Throwable ignored) {
            clearCompletedToken();
            publicationFailures++;
            return false;
        }
    }

    /**
     * Publishes a coalescing token after the semantic refresh has committed. All fallible reads
     * happen before publication and the market id is the final validity marker. Failure only
     * disables future coalescing; it must never escape and make the caller repeat the committed
     * work globally.
     */
    void recordCompleted(MarketAPI market) {
        clearCompletedToken();
        try {
            if (market == null) {
                invalidate("incomplete-market-refresh");
                return;
            }
            String marketId = market.getId();
            if (marketId == null || MarketRegistry.needsDerivedRefresh(market)) {
                invalidate("incomplete-market-refresh");
                return;
            }

            long campaignEpoch = AoTDRuntimeEpoch.getCampaignEpoch();
            long economyEpoch = AoTDRuntimeEpoch.getEconomyEpoch();
            long registryGeneration = MarketRegistry.getRegistryGeneration();
            long dirtyGeneration = MarketRegistry.getDirtyGeneration();
            long marketDirtyGeneration = MarketRegistry.getMarketDirtyGeneration(market);
            int marketIdentityHash = System.identityHashCode(market);

            completedCampaignEpoch = campaignEpoch;
            completedEconomyEpoch = economyEpoch;
            completedRegistryGeneration = registryGeneration;
            completedDirtyGeneration = dirtyGeneration;
            completedMarketDirtyGeneration = marketDirtyGeneration;
            completedMarketIdentityHash = marketIdentityHash;
            completedMarketId = marketId;
            completedRefreshes++;
        } catch (Throwable ignored) {
            clearCompletedToken();
            publicationFailures++;
        }
    }

    void recordSkip() {
        skippedRefreshes++;
    }

    void recordSyntheticCargoSkip() {
        syntheticCargoSkips++;
    }

    void recordConditionOnlySkip() {
        conditionOnlySkips++;
    }

    void invalidate(String reason) {
        clearCompletedToken();
        invalidations++;
        try {
            AoTDEconomySemanticBaseline.operation("ui-economy.refresh-invalidated", 1L);
        } catch (Throwable ignored) {
            // Diagnostics must not prevent the following global economy step.
        }
    }

    private void clearCompletedToken() {
        completedCampaignEpoch = 0L;
        completedEconomyEpoch = 0L;
        completedRegistryGeneration = 0L;
        completedDirtyGeneration = 0L;
        completedMarketDirtyGeneration = 0L;
        completedMarketIdentityHash = 0;
        completedMarketId = null;
    }

    String statusSummary() {
        return "completed="
                + completedRefreshes
                + ", skipped="
                + skippedRefreshes
                + ", syntheticCargoSkipped="
                + syntheticCargoSkips
                + ", conditionOnlySkipped="
                + conditionOnlySkips
                + ", invalidations="
                + invalidations
                + ", publicationFailures="
                + publicationFailures
                + ", market="
                + completedMarketId
                + ", campaignEpoch="
                + completedCampaignEpoch
                + ", economyEpoch="
                + completedEconomyEpoch
                + ", registryGeneration="
                + completedRegistryGeneration
                + ", dirtyGeneration="
                + completedDirtyGeneration
                + ", marketDirtyGeneration="
                + completedMarketDirtyGeneration;
    }
}
