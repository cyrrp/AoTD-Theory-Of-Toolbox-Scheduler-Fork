package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.econ.Market;
import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.PrepatcherContract;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDSupplyDemandData;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

/** Coordinates derived-state invalidation after Starsector has restored every economy object. */
public final class AoTDEconomyRestoreCoordinator {
    private static final Object LOCK = new Object();
    private static final ArrayList<WeakReference<MarketAPI>> pendingMarkets = new ArrayList<>();

    private static final int RESTORE_DIRTY_MASK =
            SchedulerBridge.DIRTY_DERIVED_ECONOMY
                    | MarketRegistry.DIRTY_PRICE
                    | MarketRegistry.DIRTY_STOCKPILE
                    | MarketRegistry.DIRTY_ACCESSIBILITY
                    | MarketRegistry.DIRTY_TRADE
                    | MarketRegistry.DIRTY_GLOBAL_REVISION;

    private AoTDEconomyRestoreCoordinator() {}

    /** Called from an individual industry callback; records identity and performs no live reads. */
    public static void recordMarketForRestore(MarketAPI market) {
        if (market == null) return;
        synchronized (LOCK) {
            pruneCollectedLocked();
            for (WeakReference<MarketAPI> reference : pendingMarkets) {
                if (reference.get() == market) return;
            }
            pendingMarkets.add(new WeakReference<>(market));
        }
    }

    /** Entry point for the loader-local Runnable registered with Prepatcher. */
    public static void signalRestoreComplete() {
        AoTDEconomy economy = currentEconomy();
        if (economy == null) return;
        recordEconomyMarkets(economy);

        // A load signal arrives before the mod plugin binds the new campaign epoch and rebuilds the
        // registry. Keep weak pending identities for onGameLoad instead of publishing into stale
        // loader-local state. A normal save keeps both identity and registry current and can
        // publish
        // immediately while the worker save barrier remains active.
        if (AoTDRuntimeEpoch.isCurrentEconomy(economy)
                && MarketRegistry.getRegistryLifecycle()
                        == MarketRegistry.RegistryLifecycle.READY) {
            consumePending(economy, true);
        }
    }

    /**
     * Consumes the load-time signal before onGameLoad performs its initial-dirty registry rebuild.
     */
    public static void consumeOnGameLoad(AoTDEconomy economy) {
        if (economy == null) return;
        recordEconomyMarkets(economy);
        consumePending(economy, false);
    }

    /**
     * Fallback for save completion/failure. With the negotiated hook this normally drains nothing;
     * after a capability loss it records the complete economy before draining.
     */
    public static void consumeAfterSave(AoTDEconomy economy) {
        if (economy == null) return;
        if (!SchedulerBridge.hasCapability(
                PrepatcherContract.CAPABILITY_ECONOMY_RESTORE_COORDINATION)) {
            recordEconomyMarkets(economy);
        }
        consumePending(economy, true);
    }

    /**
     * Failure fallback when Core's restore method did not reach the injected completion hook. Every
     * current market is invalidated because any subset of industries may already have replaced its
     * save-cleanup maps before the failure escaped.
     */
    public static void consumeAfterSaveFailure(AoTDEconomy economy) {
        if (economy == null) return;
        recordEconomyMarkets(economy);
        consumePending(economy, true);
    }

    private static AoTDEconomy currentEconomy() {
        if (Global.getSector() == null
                || !(Global.getSector().getEconomy() instanceof AoTDEconomy economy)) {
            return null;
        }
        return economy;
    }

    private static void recordEconomyMarkets(AoTDEconomy economy) {
        for (MarketAPI market : economy.getMarketsCopy()) {
            recordMarketForRestore(market);
        }
    }

    private static void consumePending(AoTDEconomy economy, boolean publishDirty) {
        List<MarketAPI> currentMarkets = economy.getMarketsCopy();
        IdentityHashMap<MarketAPI, Boolean> currentIdentities = new IdentityHashMap<>();
        for (MarketAPI market : currentMarkets) {
            if (market != null) currentIdentities.put(market, Boolean.TRUE);
        }

        ArrayList<MarketAPI> targets = new ArrayList<>();
        synchronized (LOCK) {
            IdentityHashMap<MarketAPI, Boolean> added = new IdentityHashMap<>();
            Iterator<WeakReference<MarketAPI>> iterator = pendingMarkets.iterator();
            while (iterator.hasNext()) {
                MarketAPI market = iterator.next().get();
                if (market == null || !currentIdentities.containsKey(market)) {
                    iterator.remove();
                    continue;
                }
                if (added.put(market, Boolean.TRUE) == null) targets.add(market);
            }
        }

        RuntimeException firstFailure = null;
        int failedMarkets = 0;
        ArrayList<MarketAPI> completed = new ArrayList<>();
        for (MarketAPI market : targets) {
            try {
                if (!(market instanceof Market concreteMarket)) {
                    throw new IllegalStateException(
                            "AoTD restore target is not a Starsector Market: "
                                    + market.getClass().getName());
                }
                boolean structureChanged =
                        AoTDEconomy.reconcileCommodityStructureWithoutRefresh(concreteMarket);
                discardDerivedIndustrySnapshots(market);
                if (publishDirty) {
                    int dirtyMask =
                            RESTORE_DIRTY_MASK
                                    | (structureChanged ? SchedulerBridge.DIRTY_STRUCTURE : 0);
                    MarketRegistry.markDirty(market, dirtyMask, MarketRegistry.PRIORITY_NORMAL);
                }
                completed.add(market);
            } catch (RuntimeException failure) {
                failedMarkets++;
                if (firstFailure == null) firstFailure = failure;
            }
        }

        synchronized (LOCK) {
            Iterator<WeakReference<MarketAPI>> iterator = pendingMarkets.iterator();
            while (iterator.hasNext()) {
                MarketAPI pending = iterator.next().get();
                if (pending == null || containsIdentity(completed, pending)) iterator.remove();
            }
        }

        if (firstFailure != null) {
            Global.getLogger(AoTDEconomyRestoreCoordinator.class)
                    .error(
                            "AoTD post-restore structural reconciliation failed for "
                                    + failedMarkets
                                    + " market(s); keeping them pending for a later retry.",
                            firstFailure);
        }
    }

    private static void discardDerivedIndustrySnapshots(MarketAPI market) {
        for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
            if (!(commodity instanceof AoTDCommodityOnMarket aotdCommodity)) continue;
            AoTDSupplyDemandData data = aotdCommodity.peekSupplyDemandData();
            if (data != null) data.discardDerivedIndustrySnapshot();
        }
    }

    private static boolean containsIdentity(List<MarketAPI> markets, MarketAPI target) {
        for (MarketAPI market : markets) {
            if (market == target) return true;
        }
        return false;
    }

    private static void pruneCollectedLocked() {
        pendingMarkets.removeIf(reference -> reference.get() == null);
    }
}
