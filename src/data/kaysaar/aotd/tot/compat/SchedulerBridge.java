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
    public static final int BRIDGE_SCHEMA = 5;
    public static final String BRIDGE_MARKER = "AOTD_SCHEDULER_BRIDGE_V5";

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

    private static volatile State state = State.UNINITIALIZED;
    private static volatile long negotiatedCapabilities;
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
            state = State.ABI_INCOMPATIBLE;
            diagnostic = "Prepatcher rejected AoTD scheduler ABI "
                    + PrepatcherContract.ABI_VERSION;
            return state;
        }
        negotiatedCapabilities = negotiated;
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
    public static boolean hasProductionProfile() {
        return state == State.ACTIVE
                && (negotiatedCapabilities & PrepatcherContract.PRODUCTION_CAPABILITIES)
                == PrepatcherContract.PRODUCTION_CAPABILITIES;
    }

    public static void requireProductionProfile() {
        if (hasProductionProfile()) return;
        throw new IllegalStateException(
                "StarsectorPrepatcher runtime integration is inactive or incompatible "
                        + "(required capabilities=0x"
                        + Long.toHexString(PrepatcherContract.PRODUCTION_CAPABILITIES)
                        + ", negotiated=0x" + Long.toHexString(negotiatedCapabilities)
                        + "). Verify that the Prepatcher javaagent is installed and active. "
                        + "Bridge status: " + statusSummary());
    }

    public static boolean hasCapability(long capability) {
        return state == State.ACTIVE && capability != 0L
                && (negotiatedCapabilities & capability) == capability;
    }
    public static long getNegotiatedCapabilities() { return negotiatedCapabilities; }
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
                + ", negotiated=0x" + Long.toHexString(negotiatedCapabilities)
                + ", deliveredSignals=" + deliveredSignals.get()
                + ", listenerFailures=" + deliveryListenerFailures.get()
                + ", " + diagnostic;
    }

    static synchronized void resetForTests() {
        state = State.UNINITIALIZED;
        negotiatedCapabilities = 0L;
        diagnostic = "not initialized";
        deliveryListener = null;
        deliveredSignals.set(0L);
        deliveryListenerFailures.set(0L);
        lastDeliveredGeneration = 0L;
        lastDeliverySequence = 0L;
        lastDeliveredAmount = 0f;
        MarketRegistry.clear();
    }
}
