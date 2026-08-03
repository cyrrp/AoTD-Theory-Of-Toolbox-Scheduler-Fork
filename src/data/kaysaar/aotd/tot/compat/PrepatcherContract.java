package data.kaysaar.aotd.tot.compat;

/**
 * Static ABI marker for the AoTD scheduler fork.
 *
 * <p>The class has no Starsector, Prepatcher or reflection dependency. The
 * javaagent reads its classfile marker and patches the fork-owned no-op bridge;
 * no mod-side loader probing is performed.</p>
 */
public final class PrepatcherContract {
    public static final String MOD_ID = "aotd_theory_of_toolbox";
    public static final String MARKER_CLASS =
            "data.kaysaar.aotd.tot.compat.PrepatcherContract";
    public static final String FORK_VERSION = "1.0.14-spp8";

    /** Loader-safe javaagent patch and capability negotiation. */
    public static final long CAPABILITY_CONTRACT_HANDSHAKE = 1L;
    /** Callback after a concrete Market.advance invocation returned successfully. */
    public static final long CAPABILITY_NATIVE_DELIVERY_EVENTS = 1L << 1;
    /** Source-level market mutation boundaries with exact pending-time replay. */
    public static final long CAPABILITY_NATIVE_MUTATION_BOUNDARIES = 1L << 2;
    /** Delivered and structural generation exchange. */
    public static final long CAPABILITY_MARKET_GENERATIONS = 1L << 3;
    /** Reserved for the clean BaseIndustry deficit path. */
    public static final long CAPABILITY_CLEAN_DEFICIT_SEMANTICS = 1L << 4;
    /** Fork publishes authoritative per-market derived-state commits. */
    public static final long CAPABILITY_AUTHORITATIVE_MARKET_STATE = 1L << 5;
    /** Price/stockpile workers use immutable DTOs and main-thread commit. */
    public static final long CAPABILITY_PURE_PRICE_OFFLOAD = 1L << 6;
    /** Global committed cuts and hard temporal boundaries. */
    public static final long CAPABILITY_GLOBAL_PHASE_COORDINATION = 1L << 7;
    /** Campaign/economy epoch publication and stale-runtime invalidation. */
    public static final long CAPABILITY_RUNTIME_EPOCH_COORDINATION = 1L << 8;
    /** Explicit verified UI action dispatch; standard economy steps remain global. */
    public static final long CAPABILITY_UI_ECONOMY_DISPATCH = 1L << 9;
    /** Atomic UI mutation refresh and affected-commodity rebuild. */
    public static final long CAPABILITY_UI_MARKET_MUTATION_REFRESH = 1L << 10;

    /** Exact caller intents accepted by AoTDEconomy.dispatchPrepatcherUiEconomyStep. */
    public static final int UI_ECONOMY_ACTION_MARKET_OPEN = 1;
    public static final int UI_ECONOMY_ACTION_CARGO = 2;
    public static final int UI_ECONOMY_ACTION_MARKET_MUTATION = 3;
    public static final long UI_ECONOMY_CARGO_SYNTHETIC = 1L;
    public static final long UI_ECONOMY_CARGO_LIVE_MARKET = 2L;

    /** Complete production profile required by the scheduler fork. */
    public static final long PRODUCTION_CAPABILITIES =
            CAPABILITY_CONTRACT_HANDSHAKE
                    | CAPABILITY_NATIVE_DELIVERY_EVENTS
                    | CAPABILITY_NATIVE_MUTATION_BOUNDARIES
                    | CAPABILITY_MARKET_GENERATIONS
                    | CAPABILITY_CLEAN_DEFICIT_SEMANTICS
                    | CAPABILITY_AUTHORITATIVE_MARKET_STATE
                    | CAPABILITY_PURE_PRICE_OFFLOAD
                    | CAPABILITY_GLOBAL_PHASE_COORDINATION
                    | CAPABILITY_RUNTIME_EPOCH_COORDINATION
                    | CAPABILITY_UI_ECONOMY_DISPATCH;

    public static final long DECLARED_CAPABILITIES =
            PRODUCTION_CAPABILITIES | CAPABILITY_UI_MARKET_MUTATION_REFRESH;

    private PrepatcherContract() {}
}
