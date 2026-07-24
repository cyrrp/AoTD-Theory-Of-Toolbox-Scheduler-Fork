package data.kaysaar.aotd.tot.compat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/**
 * No-reflection bridge surface owned by the AoTD fork.
 *
 * <p>Without the javaagent all scheduler methods are inert no-ops. A compatible
 * Prepatcher verifies this exact surface and rewrites the method bodies to direct
 * target-loader calls before the class is defined.</p>
 */
public final class SchedulerBridge {
    public static final int BRIDGE_SCHEMA = 6;
    public static final String BRIDGE_MARKER = "AOTD_SCHEDULER_BRIDGE_V6";

    public static final int MUTATION_MARKET_MEMBERSHIP = 1;
    public static final int MUTATION_INDUSTRY_STRUCTURE = 1 << 1;
    public static final int MUTATION_CONDITION_STRUCTURE = 1 << 2;
    public static final int MUTATION_COMMODITY_STRUCTURE = 1 << 3;
    public static final int DIRTY_STRUCTURE = 1;
    public static final int DIRTY_INDUSTRIES = 1 << 1;
    public static final int DIRTY_CONDITIONS = 1 << 2;
    public static final int DIRTY_DERIVED_ECONOMY = 1 << 3;

    public enum State {
        UNINITIALIZED,
        ACTIVE,
        PREPATCHER_UNAVAILABLE,
        ABI_INCOMPATIBLE
    }

    /** Loader-local consumer passed to the parent runtime through a JDK interface. */
    /** Loader-local resolver passed through a JDK interface; no reflection. */
    private static final BiFunction<Object, Object, Object> DEFICIT_RESOLVER_SIGNAL =
            new BiFunction<Object, Object, Object>() {
                @Override
                public Object apply(Object industry, Object commodityIds) {
                    return AoTDDeficitResolver.resolve(industry, commodityIds);
                }
            };

    private static final Consumer<Object> DELIVERY_SIGNAL = new Consumer<Object>() {
        @Override
        public void accept(Object market) {
            acceptDeliveredMarket(market);
        }
    };
    private static final AtomicLong deliveredSignals = new AtomicLong();
    private static final AtomicLong deliveryListenerFailures = new AtomicLong();
    private static final AtomicLong runtimeCapabilityRefreshes = new AtomicLong();
    private static final AtomicLong runtimeCapabilityDowngrades = new AtomicLong();
    private static final AtomicLong runtimeCapabilityResynchronizations = new AtomicLong();
    private static final AtomicLong runtimeCapabilityResynchronizedMarkets = new AtomicLong();
    private static final AtomicLong runtimeCapabilityResynchronizationFailures = new AtomicLong();

    private static volatile State state = State.UNINITIALIZED;
    /** Current runtime mask. Unlike the activation snapshot, this may shrink at runtime. */
    private static volatile long negotiatedCapabilities;
    private static volatile long initialNegotiatedCapabilities;
    private static volatile long lastLostCapabilities;
    private static volatile String diagnostic = "not initialized";
    private static volatile DeliveryListener deliveryListener;
    private static volatile long lastDeliveredGeneration;
    private static volatile long lastDeliverySequence;
    private static volatile float lastDeliveredAmount;

    private SchedulerBridge() {}

    public interface DeliveryListener {
        void onMarketTimeDelivered(
                Object market, long deliveredGeneration, long deliverySequence,
                float deliveredAmount);
    }

    /** No-agent implementation; replaced atomically by the javaagent. */
    public static synchronized State initialize() {
        if (state == State.UNINITIALIZED) {
            state = State.PREPATCHER_UNAVAILABLE;
            diagnostic = "javaagent bridge patch is not installed; native scheduler ABI is a no-op";
        }
        return state;
    }

    /** Called by the verified javaagent-generated initialize body. */
    public static synchronized State activateFromPrepatcher(long negotiated) {
        if ((negotiated & PrepatcherContract.CAPABILITY_CONTRACT_HANDSHAKE) == 0L) {
            negotiatedCapabilities = 0L;
            initialNegotiatedCapabilities = 0L;
            state = State.ABI_INCOMPATIBLE;
            diagnostic = "Prepatcher rejected AoTD scheduler ABI "
                    + PrepatcherContract.ABI_VERSION;
            return state;
        }
        negotiatedCapabilities = negotiated;
        initialNegotiatedCapabilities = negotiated;
        lastLostCapabilities = 0L;
        state = State.ACTIVE;
        diagnostic = "active; capabilities=0x" + Long.toHexString(negotiated);
        return state;
    }

    /** Used only by the javaagent-generated registration call. */
    public static Consumer<Object> deliverySignalConsumer() {
        return DELIVERY_SIGNAL;
    }

    /** Used only by the javaagent-generated contract registration call. */
    public static BiFunction<Object, Object, Object> deficitResolverFunction() {
        return DEFICIT_RESOLVER_SIGNAL;
    }

    public static void setDeliveryListener(DeliveryListener listener) {
        deliveryListener = listener;
    }

    public static void clearDeliveryListener(DeliveryListener listener) {
        if (deliveryListener == listener) deliveryListener = null;
    }

    /** No-agent body; replaced with a direct runtime call. */
    public static long getDeliveredMarketGeneration(Object market) {
        return 0L;
    }

    /** No-agent body; replaced with a direct runtime call. */
    public static long getLastMarketDeliverySequence(Object market) {
        return 0L;
    }

    /** No-agent body; replaced with a direct runtime call. */
    public static float getLastMarketDeliveredAmount(Object market) {
        return 0f;
    }

    /** No-agent body; replaced with a direct runtime call. */
    public static long getMarketStructuralGeneration(Object market) {
        return 0L;
    }

    /**
     * Opens a source-level structural mutation. The active implementation first
     * delivers pending scheduler debt to the old market structure.
     */
    public static long beforeMarketMutation(Object market, int reasonMask) {
        return 0L;
    }

    /** No-agent body; replaced with a direct runtime call. */
    public static void afterMarketMutation(
            long token, Object market, int dirtyMask, long sourceGeneration) {
        acceptMarketMutation(market, dirtyMask, sourceGeneration, 0L);
    }

    /** Publishes the process-local campaign/economy identity to Prepatcher. */
    public static void publishRuntimeEpoch(long campaignEpoch, long economyEpoch) {
        // no-agent loader fallback
    }

    /**
     * Returns the capability mask currently active in Prepatcher. The no-agent
     * body returns the local activation snapshot; the javaagent replaces it
     * with a direct target-loader call.
     */
    public static long getRuntimeCapabilities() {
        return negotiatedCapabilities;
    }

    /** Opens a global committed cut; hardFlush requests delivery of all pending market time. */
    public static long beforeGlobalBoundary(int reasonMask, boolean hardFlush) {
        return 0L;
    }

    /** Closes a global committed cut and publishes its revision to Prepatcher diagnostics. */
    public static void afterGlobalBoundary(long token, long generation) {
        // no-agent loader fallback
    }

    /** Called by the javaagent-generated after-mutation body after runtime commit. */
    public static void acceptMarketMutation(
            Object market, int dirtyMask, long sourceGeneration,
            long structuralGeneration) {
        MarketRegistry.onMarketMutationCommitted(
                market, dirtyMask, sourceGeneration, structuralGeneration);
    }

    static void acceptDeliveredMarket(Object market) {
        long generation = getDeliveredMarketGeneration(market);
        long sequence = getLastMarketDeliverySequence(market);
        float amount = getLastMarketDeliveredAmount(market);
        deliveredSignals.incrementAndGet();
        MarketRegistry.onMarketTimeDelivered(market, generation, sequence, amount);
        lastDeliveredGeneration = generation;
        lastDeliverySequence = sequence;
        lastDeliveredAmount = amount;
        DeliveryListener listener = deliveryListener;
        if (listener == null) return;
        try {
            listener.onMarketTimeDelivered(market, generation, sequence, amount);
        } catch (Throwable failure) {
            deliveryListenerFailures.incrementAndGet();
            diagnostic = "delivery listener failed: " + failure.getClass().getName();
        }
    }

    public static State getState() { return state; }
    public static boolean isActive() { return state == State.ACTIVE; }

    /**
     * Refreshes the loader-local capability snapshot from the live Prepatcher
     * runtime. A runtime downgrade is fail-stop: capabilities may disappear but
     * are never assumed to reappear without a new bridge activation.
     */
    private static long refreshRuntimeCapabilities() {
        if (state != State.ACTIVE) return negotiatedCapabilities;
        long observed = getRuntimeCapabilities();
        long current = negotiatedCapabilities;
        if (observed == current) return current;

        long lost;
        boolean resynchronize = false;
        synchronized (SchedulerBridge.class) {
            current = negotiatedCapabilities;
            if (observed == current) return current;
            // Only accept a subset of the original handshake. A runtime bridge
            // must not silently grant capabilities that were never negotiated.
            observed &= initialNegotiatedCapabilities;
            if (observed == current) return current;
            lost = current & ~observed;
            negotiatedCapabilities = observed;
            lastLostCapabilities = lost;
            runtimeCapabilityRefreshes.incrementAndGet();
            if (lost != 0L) runtimeCapabilityDowngrades.incrementAndGet();
            diagnostic = "runtime capabilities changed: current=0x"
                    + Long.toHexString(observed) + ", lost=0x"
                    + Long.toHexString(lost);
            resynchronize = (lost & PrepatcherContract.CAPABILITY_NATIVE_DELIVERY_EVENTS) != 0L
                    && (observed & PrepatcherContract.CAPABILITY_MARKET_GENERATIONS) != 0L;
        }

        if (resynchronize) {
            runtimeCapabilityResynchronizations.incrementAndGet();
            try {
                int repaired = MarketRegistry.resynchronizeRuntimeGenerations();
                runtimeCapabilityResynchronizedMarkets.addAndGet(repaired);
                diagnostic = "native delivery events disabled at runtime; resynchronized "
                        + repaired + " markets; capabilities=0x"
                        + Long.toHexString(observed);
            } catch (Throwable failure) {
                runtimeCapabilityResynchronizationFailures.incrementAndGet();
                diagnostic = "runtime capability downgrade resynchronization failed: "
                        + failure.getClass().getName();
            }
        }
        return observed;
    }

    public static boolean hasProductionProfile() {
        long capabilities = refreshRuntimeCapabilities();
        return state == State.ACTIVE
                && (capabilities & PrepatcherContract.PRODUCTION_CAPABILITIES)
                == PrepatcherContract.PRODUCTION_CAPABILITIES;
    }

    public static void requireProductionProfile() {
        if (hasProductionProfile()) return;
        throw new IllegalStateException(
                "StarsectorPrepatcher runtime integration is inactive or incompatible "
                        + "(required capabilities=0x"
                        + Long.toHexString(PrepatcherContract.PRODUCTION_CAPABILITIES)
                        + ", negotiated=0x" + Long.toHexString(refreshRuntimeCapabilities())
                        + "). Verify that the Prepatcher javaagent is installed and active. "
                        + "Bridge status: " + statusSummary());
    }

    public static boolean hasCapability(long capability) {
        long capabilities = refreshRuntimeCapabilities();
        return state == State.ACTIVE && capability != 0L
                && (capabilities & capability) == capability;
    }
    public static long getNegotiatedCapabilities() {
        return refreshRuntimeCapabilities();
    }
    public static long getInitialNegotiatedCapabilities() {
        return initialNegotiatedCapabilities;
    }
    public static long getLastLostCapabilities() { return lastLostCapabilities; }
    public static long getRuntimeCapabilityRefreshCount() {
        return runtimeCapabilityRefreshes.get();
    }
    public static long getRuntimeCapabilityDowngradeCount() {
        return runtimeCapabilityDowngrades.get();
    }
    public static long getRuntimeCapabilityResynchronizationCount() {
        return runtimeCapabilityResynchronizations.get();
    }
    public static long getRuntimeCapabilityResynchronizedMarketCount() {
        return runtimeCapabilityResynchronizedMarkets.get();
    }
    public static long getRuntimeCapabilityResynchronizationFailureCount() {
        return runtimeCapabilityResynchronizationFailures.get();
    }
    public static String getDiagnostic() { return diagnostic; }
    public static long getDeliveredSignalCount() { return deliveredSignals.get(); }
    public static long getDeliveryListenerFailureCount() {
        return deliveryListenerFailures.get();
    }
    public static long getLastDeliveredGeneration() { return lastDeliveredGeneration; }
    public static long getLastDeliverySequence() { return lastDeliverySequence; }
    public static float getLastDeliveredAmount() { return lastDeliveredAmount; }

    public static String statusSummary() {
        return "state=" + state + ", declared=0x"
                + Long.toHexString(PrepatcherContract.DECLARED_CAPABILITIES)
                + ", initialNegotiated=0x" + Long.toHexString(initialNegotiatedCapabilities)
                + ", runtimeNegotiated=0x" + Long.toHexString(refreshRuntimeCapabilities())
                + ", lastLost=0x" + Long.toHexString(lastLostCapabilities)
                + ", capabilityRefreshes=" + runtimeCapabilityRefreshes.get()
                + ", capabilityDowngrades=" + runtimeCapabilityDowngrades.get()
                + ", capabilityResyncs=" + runtimeCapabilityResynchronizations.get()
                + ", capabilityResyncedMarkets=" + runtimeCapabilityResynchronizedMarkets.get()
                + ", capabilityResyncFailures=" + runtimeCapabilityResynchronizationFailures.get()
                + ", deliveredSignals=" + deliveredSignals.get()
                + ", listenerFailures=" + deliveryListenerFailures.get()
                + ", " + diagnostic;
    }

    static synchronized void resetForTests() {
        state = State.UNINITIALIZED;
        negotiatedCapabilities = 0L;
        initialNegotiatedCapabilities = 0L;
        lastLostCapabilities = 0L;
        diagnostic = "not initialized";
        deliveryListener = null;
        deliveredSignals.set(0L);
        deliveryListenerFailures.set(0L);
        runtimeCapabilityRefreshes.set(0L);
        runtimeCapabilityDowngrades.set(0L);
        runtimeCapabilityResynchronizations.set(0L);
        runtimeCapabilityResynchronizedMarkets.set(0L);
        runtimeCapabilityResynchronizationFailures.set(0L);
        lastDeliveredGeneration = 0L;
        lastDeliverySequence = 0L;
        lastDeliveredAmount = 0f;
        MarketRegistry.clear();
    }
}
