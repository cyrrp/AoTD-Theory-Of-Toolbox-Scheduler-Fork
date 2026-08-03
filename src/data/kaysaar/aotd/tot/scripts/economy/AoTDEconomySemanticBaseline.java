package data.kaysaar.aotd.tot.scripts.economy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary, source-level semantic baseline instrumentation for the scheduler fork.
 *
 * <p>This class deliberately observes the current implementation without changing
 * its ordering or calculated values. It records phase timings, worker/main-thread
 * attribution, operation counts and before/after market fingerprints for a bounded
 * deterministic sample of markets. The collected files are intended to answer
 * which reconciliation passes change authoritative economy state before those
 * passes are refactored.</p>
 */
public final class AoTDEconomySemanticBaseline {
    private AoTDEconomySemanticBaseline() {}

    public static final String MOD_ID = "aotd_theory_of_toolbox";
    public static final String SETTING_ENABLED = "aotd_semantic_baseline_enabled";
    public static final String SETTING_DEEP = "aotd_semantic_baseline_deep_snapshots";
    public static final String SETTING_MARKET_LIMIT = "aotd_semantic_baseline_market_sample_limit";
    public static final String SETTING_EVENT_LIMIT = "aotd_semantic_baseline_event_limit";
    public static final String SETTING_DIFF_LIMIT = "aotd_semantic_baseline_diff_limit";

    private static final Object LOCK = new Object();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private static final com.sun.management.ThreadMXBean ALLOCATION_BEAN = allocationBean();

    private static volatile boolean initialized;
    private static volatile boolean enabled;
    private static volatile boolean deepSnapshots;
    private static volatile int marketSampleLimit = 24;
    private static volatile int eventLimit = 200_000;
    private static volatile int diffLimit = 250_000;
    private static volatile Path sessionDirectory;
    private static volatile long sessionStartedNanos;
    private static volatile String sessionStartedUtc;
    private static volatile long economyRevision;

    private static final LinkedHashMap<String, PhaseStats> PHASES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Long> OPERATIONS = new LinkedHashMap<>();
    private static final ArrayList<EventRow> EVENTS = new ArrayList<>();
    private static final ArrayList<SnapshotRow> SNAPSHOTS = new ArrayList<>();
    private static final ArrayList<DiffRow> DIFFS = new ArrayList<>();
    private static final ArrayList<TradeSnapshotRow> TRADE_SNAPSHOTS = new ArrayList<>();
    private static final LinkedHashSet<String> SAMPLED_MARKETS = new LinkedHashSet<>();
    private static long droppedEvents;
    private static long droppedSnapshots;
    private static long droppedDiffs;

    public static void initialize() {
        try {
            synchronized (LOCK) {
                if (initialized) return;
                initialized = true;
                enabled = readBoolean(SETTING_ENABLED, false);
                deepSnapshots = enabled && readBoolean(SETTING_DEEP, true);
                marketSampleLimit = readPositiveInt(SETTING_MARKET_LIMIT, marketSampleLimit);
                eventLimit = readPositiveInt(SETTING_EVENT_LIMIT, eventLimit);
                diffLimit = readPositiveInt(SETTING_DIFF_LIMIT, diffLimit);
                sessionStartedNanos = System.nanoTime();
                sessionStartedUtc = Instant.now().toString();
                if (!enabled) return;

                String root = Global.getSettings().getModManager().getModSpec(MOD_ID).getPath();
                sessionDirectory = Path.of(root, "logs", "semantic-baseline",
                        "session-" + SESSION_TIME.format(Instant.now()));
                Files.createDirectories(sessionDirectory);
                logInfoBestEffort("AoTD semantic baseline enabled: " + sessionDirectory);
            }
        } catch (Throwable failure) {
            disableBestEffort();
            logErrorBestEffort("Could not initialize AoTD semantic baseline", failure);
        }
    }

    public static boolean isEnabled() {
        try {
            if (!initialized) initialize();
            return enabled;
        } catch (Throwable ignored) {
            disableBestEffort();
            return false;
        }
    }

    public static long beginEconomyRevision(String reason) {
        try {
            if (!isEnabled()) return 0L;
            long revision;
            synchronized (LOCK) {
                revision = ++economyRevision;
            }
            operation("economy.revision.begin", null);
            event("economy-revision", null, reason, 0L, 0L, "BEGIN", revision);
            return revision;
        } catch (Throwable ignored) {
            disableBestEffort();
            return 0L;
        }
    }

    public static void endEconomyRevision(long revision, String reason) {
        try {
            if (!isEnabled() || revision <= 0L) return;
            operation("economy.revision.end", null);
            event("economy-revision", null, reason, 0L, 0L, "END", revision);
            flush("economy-revision-" + revision + '-' + safeName(reason));
        } catch (Throwable ignored) {
            disableBestEffort();
        }
    }

    public static Scope begin(String phase) {
        return begin(phase, null, "", false);
    }

    public static Scope begin(String phase, MarketAPI market) {
        return begin(phase, market, "", false);
    }

    public static Scope begin(String phase, MarketAPI market, String detail) {
        return begin(phase, market, detail, false);
    }

    public static Scope beginMarketMutation(String phase, MarketAPI market, String detail) {
        return begin(phase, market, detail, true);
    }

    private static Scope begin(String phase, MarketAPI market, String detail, boolean snapshot) {
        try {
            if (!isEnabled()) return Scope.NOOP;
            MarketSnapshot before = snapshot && shouldSample(market)
                    ? MarketSnapshot.capture(market) : null;
            return new Scope(phase, marketId(market), detail,
                    System.nanoTime(), allocatedBytes(), before);
        } catch (Throwable ignored) {
            disableBestEffort();
            return Scope.NOOP;
        }
    }

    public static void operation(String operation, MarketAPI market) {
        try {
            if (!isEnabled()) return;
            String key = operation + (market == null ? "" : "|market");
            synchronized (LOCK) {
                OPERATIONS.put(key, OPERATIONS.getOrDefault(key, 0L) + 1L);
            }
        } catch (Throwable ignored) {
            disableBestEffort();
        }
    }

    public static void operation(String operation, long count) {
        try {
            if (!isEnabled() || count <= 0L) return;
            synchronized (LOCK) {
                OPERATIONS.put(operation, OPERATIONS.getOrDefault(operation, 0L) + count);
            }
        } catch (Throwable ignored) {
            disableBestEffort();
        }
    }

    /** Captures the AoTD-owned trade snapshot after it is published for a market. */
    public static void captureTradeSnapshot(String phase, MarketAPI market) {
        try {
            if (!isEnabled() || market == null || !shouldSample(market)) return;
            AoTDTradeManager manager = AoTDTradeManager.getInstance();
            AoTDFactionTradeData faction = manager == null
                    ? null : manager.getFactionTradeData(market.getFactionId());
            AoTDMarketData data = faction == null
                    ? null : faction.getTradeData().get(market.getId());
            if (data == null) return;

            Set<String> commodityIds = new LinkedHashSet<>();
            commodityIds.addAll(data.netProductionValues.keySet());
            commodityIds.addAll(data.remainingNet.keySet());
            commodityIds.addAll(data.internalSent.keySet());
            commodityIds.addAll(data.internalReceived.keySet());
            synchronized (LOCK) {
                if (commodityIds.isEmpty()) {
                    if (TRADE_SNAPSHOTS.size() >= diffLimit) {
                        droppedDiffs++;
                        return;
                    }
                    TRADE_SNAPSHOTS.add(new TradeSnapshotRow(
                            SEQUENCE.incrementAndGet(), economyRevision, phase,
                            market.getId(), market.getFactionId(), "", 0, 0, 0, 0,
                            data.weight, data.outsideWeight));
                } else {
                    ArrayList<String> sorted = new ArrayList<>(commodityIds);
                    sorted.sort(Comparator.naturalOrder());
                    for (String commodityId : sorted) {
                        if (TRADE_SNAPSHOTS.size() >= diffLimit) {
                            droppedDiffs++;
                            break;
                        }
                        TRADE_SNAPSHOTS.add(new TradeSnapshotRow(
                                SEQUENCE.incrementAndGet(), economyRevision, phase,
                                market.getId(), market.getFactionId(), commodityId,
                                data.netProductionValues.getOrDefault(commodityId, 0),
                                data.remainingNet.getOrDefault(commodityId, 0),
                                data.internalSent.getOrDefault(commodityId, 0),
                                data.internalReceived.getOrDefault(commodityId, 0),
                                data.weight, data.outsideWeight));
                    }
                }
            }
        } catch (Throwable ignored) {
            disableBestEffort();
        }
    }

    public static void flush(String reason) {
        try {
            if (!isEnabled()) return;
            Path directory = sessionDirectory;
            if (directory == null) return;

            String summary;
            String operations;
            String events;
            String snapshots;
            String diffs;
            String tradeSnapshots;
            String metadata;
            synchronized (LOCK) {
                summary = phaseSummaryCsv();
                operations = operationsCsv();
                events = eventsCsv();
                snapshots = snapshotsCsv();
                diffs = diffsCsv();
                tradeSnapshots = tradeSnapshotsCsv();
                metadata = metadataJson(reason);
            }

            write(directory.resolve("phase-summary.csv"), summary);
            write(directory.resolve("operation-counts.csv"), operations);
            write(directory.resolve("phase-events.csv"), events);
            write(directory.resolve("market-snapshots.csv"), snapshots);
            write(directory.resolve("semantic-diffs.csv"), diffs);
            write(directory.resolve("trade-snapshots.csv"), tradeSnapshots);
            write(directory.resolve("session.json"), metadata);
        } catch (Throwable failure) {
            disableBestEffort();
            logErrorBestEffort("Could not flush AoTD semantic baseline", failure);
        }
    }

    private static void finish(Scope scope) {
        if (scope == null || scope == Scope.NOOP || !enabled) return;
        long elapsed = Math.max(0L, System.nanoTime() - scope.startedNanos);
        long allocated = allocationDelta(scope.startedAllocated, allocatedBytes());
        MarketSnapshot after = scope.before == null ? null : MarketSnapshot.captureById(scope.marketId);

        synchronized (LOCK) {
            PhaseStats stats = PHASES.computeIfAbsent(scope.phase, ignored -> new PhaseStats());
            stats.calls++;
            stats.totalNanos += elapsed;
            stats.maxNanos = Math.max(stats.maxNanos, elapsed);
            if (allocated >= 0L) {
                stats.allocatedSamples++;
                stats.totalAllocatedBytes += allocated;
                stats.maxAllocatedBytes = Math.max(stats.maxAllocatedBytes, allocated);
            }
            if (scope.failed) stats.failures++;
            if (scope.workerThread) stats.workerCalls++;
            else stats.nonWorkerCalls++;

            appendEvent(new EventRow(SEQUENCE.incrementAndGet(), economyRevision,
                    scope.phase, scope.marketId, scope.detail, scope.threadName,
                    elapsed, allocated, scope.failed ? "FAIL" : "OK"));
            if (scope.before != null) {
                appendSnapshot(scope.phase, "BEFORE", scope.before);
                if (after != null) {
                    appendSnapshot(scope.phase, "AFTER", after);
                    appendDiffs(scope.phase, scope.before, after);
                }
            }
        }
    }

    private static void event(String phase, MarketAPI market, String detail,
                              long elapsed, long allocated, String outcome, long revision) {
        synchronized (LOCK) {
            appendEvent(new EventRow(SEQUENCE.incrementAndGet(), revision, phase,
                    marketId(market), detail, Thread.currentThread().getName(),
                    elapsed, allocated, outcome));
        }
    }

    private static void appendEvent(EventRow row) {
        if (EVENTS.size() >= eventLimit) {
            droppedEvents++;
            return;
        }
        EVENTS.add(row);
    }

    private static void appendSnapshot(String phase, String moment, MarketSnapshot snapshot) {
        if (SNAPSHOTS.size() >= diffLimit) {
            droppedSnapshots++;
            return;
        }
        SNAPSHOTS.add(new SnapshotRow(SEQUENCE.incrementAndGet(), economyRevision, phase, moment,
                snapshot.marketId, snapshot.factionId, snapshot.econGroup,
                snapshot.industryCount, snapshot.commodityCount, snapshot.pendingIndustries,
                snapshot.disruptedIndustries, snapshot.buildingIndustries,
                snapshot.upgradingIndustries, snapshot.rawSupply, snapshot.rawDemand,
                snapshot.stockpile, snapshot.deficit, snapshot.excess,
                snapshot.valueCount, snapshot.fingerprint));
    }

    private static void appendDiffs(String phase, MarketSnapshot before, MarketSnapshot after) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.values.keySet());
        keys.addAll(after.values.keySet());
        for (String key : keys) {
            double oldValue = before.values.getOrDefault(key, Double.NaN);
            double newValue = after.values.getOrDefault(key, Double.NaN);
            if (same(oldValue, newValue)) continue;
            if (DIFFS.size() >= diffLimit) {
                droppedDiffs++;
                continue;
            }
            DIFFS.add(new DiffRow(SEQUENCE.incrementAndGet(), economyRevision, phase,
                    before.marketId, key, oldValue, newValue));
        }
    }

    private static boolean shouldSample(MarketAPI market) {
        if (!deepSnapshots || market == null || marketSampleLimit <= 0) return false;
        String id = marketId(market);
        synchronized (LOCK) {
            if (SAMPLED_MARKETS.contains(id)) return true;
            if (SAMPLED_MARKETS.size() >= marketSampleLimit) return false;
            SAMPLED_MARKETS.add(id);
            return true;
        }
    }

    private static String phaseSummaryCsv() {
        StringBuilder out = new StringBuilder(
                "phase,calls,failures,worker_calls,non_worker_calls,total_ms,mean_ms,max_ms,allocated_samples,total_allocated_bytes,max_allocated_bytes\n");
        for (Map.Entry<String, PhaseStats> entry : PHASES.entrySet()) {
            PhaseStats value = entry.getValue();
            double totalMs = value.totalNanos / 1_000_000d;
            double meanMs = value.calls == 0L ? 0d : totalMs / value.calls;
            out.append(csv(entry.getKey())).append(',').append(value.calls).append(',')
                    .append(value.failures).append(',').append(value.workerCalls).append(',')
                    .append(value.nonWorkerCalls).append(',').append(format(totalMs)).append(',')
                    .append(format(meanMs)).append(',').append(format(value.maxNanos / 1_000_000d)).append(',')
                    .append(value.allocatedSamples).append(',').append(value.totalAllocatedBytes).append(',')
                    .append(value.maxAllocatedBytes).append('\n');
        }
        return out.toString();
    }

    private static String operationsCsv() {
        StringBuilder out = new StringBuilder("operation,count\n");
        for (Map.Entry<String, Long> entry : OPERATIONS.entrySet()) {
            out.append(csv(entry.getKey())).append(',').append(entry.getValue()).append('\n');
        }
        return out.toString();
    }

    private static String eventsCsv() {
        StringBuilder out = new StringBuilder(
                "sequence,economy_revision,phase,market_id,detail,thread,duration_nanos,allocated_bytes,outcome\n");
        for (EventRow row : EVENTS) {
            out.append(row.sequence).append(',').append(row.revision).append(',')
                    .append(csv(row.phase)).append(',').append(csv(row.marketId)).append(',')
                    .append(csv(row.detail)).append(',').append(csv(row.thread)).append(',')
                    .append(row.durationNanos).append(',').append(row.allocatedBytes).append(',')
                    .append(row.outcome).append('\n');
        }
        return out.toString();
    }

    private static String snapshotsCsv() {
        StringBuilder out = new StringBuilder(
                "sequence,economy_revision,phase,moment,market_id,faction_id,econ_group,industries,commodities,pending_industries,disrupted_industries,building_industries,upgrading_industries,raw_supply,raw_demand,stockpile,deficit,excess,value_count,fingerprint\n");
        for (SnapshotRow row : SNAPSHOTS) {
            out.append(row.sequence).append(',').append(row.revision).append(',')
                    .append(csv(row.phase)).append(',').append(row.moment).append(',')
                    .append(csv(row.marketId)).append(',').append(csv(row.factionId)).append(',')
                    .append(csv(row.econGroup)).append(',').append(row.industries).append(',')
                    .append(row.commodities).append(',').append(row.pendingIndustries).append(',')
                    .append(row.disruptedIndustries).append(',').append(row.buildingIndustries).append(',')
                    .append(row.upgradingIndustries).append(',').append(format(row.rawSupply)).append(',')
                    .append(format(row.rawDemand)).append(',').append(format(row.stockpile)).append(',')
                    .append(format(row.deficit)).append(',').append(format(row.excess)).append(',')
                    .append(row.valueCount).append(',').append(Long.toUnsignedString(row.fingerprint)).append('\n');
        }
        return out.toString();
    }

    private static String diffsCsv() {
        StringBuilder out = new StringBuilder(
                "sequence,economy_revision,phase,market_id,value_key,before_value,after_value,delta\n");
        for (DiffRow row : DIFFS) {
            double delta = Double.isNaN(row.beforeValue) || Double.isNaN(row.afterValue)
                    ? Double.NaN : row.afterValue - row.beforeValue;
            out.append(row.sequence).append(',').append(row.revision).append(',')
                    .append(csv(row.phase)).append(',').append(csv(row.marketId)).append(',')
                    .append(csv(row.key)).append(',').append(format(row.beforeValue)).append(',')
                    .append(format(row.afterValue)).append(',').append(format(delta)).append('\n');
        }
        return out.toString();
    }

    private static String tradeSnapshotsCsv() {
        StringBuilder out = new StringBuilder(
                "sequence,economy_revision,phase,market_id,faction_id,commodity_id,net_production,remaining_net,internal_sent,internal_received,weight,outside_weight\n");
        for (TradeSnapshotRow row : TRADE_SNAPSHOTS) {
            out.append(row.sequence).append(',').append(row.revision).append(',')
                    .append(csv(row.phase)).append(',').append(csv(row.marketId)).append(',')
                    .append(csv(row.factionId)).append(',').append(csv(row.commodityId)).append(',')
                    .append(row.netProduction).append(',').append(row.remainingNet).append(',')
                    .append(row.internalSent).append(',').append(row.internalReceived).append(',')
                    .append(format(row.weight)).append(',').append(format(row.outsideWeight)).append('\n');
        }
        return out.toString();
    }

    private static String metadataJson(String reason) {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"sessionStartedUtc\": \"" + json(sessionStartedUtc) + "\",\n"
                + "  \"lastFlushUtc\": \"" + json(Instant.now().toString()) + "\",\n"
                + "  \"flushReason\": \"" + json(reason) + "\",\n"
                + "  \"elapsedNanos\": " + Math.max(0L, System.nanoTime() - sessionStartedNanos) + ",\n"
                + "  \"economyRevision\": " + economyRevision + ",\n"
                + "  \"deepSnapshots\": " + deepSnapshots + ",\n"
                + "  \"sampledMarkets\": " + SAMPLED_MARKETS.size() + ",\n"
                + "  \"eventRows\": " + EVENTS.size() + ",\n"
                + "  \"droppedEvents\": " + droppedEvents + ",\n"
                + "  \"snapshotRows\": " + SNAPSHOTS.size() + ",\n"
                + "  \"droppedSnapshots\": " + droppedSnapshots + ",\n"
                + "  \"diffRows\": " + DIFFS.size() + ",\n"
                + "  \"tradeSnapshotRows\": " + TRADE_SNAPSHOTS.size() + ",\n"
                + "  \"droppedDiffs\": " + droppedDiffs + "\n"
                + "}\n";
    }

    private static void write(Path target, String content) throws Exception {
        Files.writeString(target, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void disableBestEffort() {
        try {
            enabled = false;
            initialized = true;
            deepSnapshots = false;
        } catch (Throwable ignored) {
            // Diagnostic state has no semantic authority.
        }
    }

    private static void logInfoBestEffort(String message) {
        try {
            Global.getLogger(AoTDEconomySemanticBaseline.class).info(message);
        } catch (Throwable ignored) {
            // Logging is part of the optional diagnostic surface.
        }
    }

    private static void logErrorBestEffort(String message, Throwable failure) {
        try {
            Global.getLogger(AoTDEconomySemanticBaseline.class).error(message, failure);
        } catch (Throwable ignored) {
            // Logging is part of the optional diagnostic surface.
        }
    }

    private static boolean readBoolean(String key, boolean fallback) {
        try {
            return Global.getSettings().getBoolean(key);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int readPositiveInt(String key, int fallback) {
        try {
            float value = Global.getSettings().getFloat(key);
            if (Float.isFinite(value) && value > 0f) return Math.max(1, Math.round(value));
        } catch (Throwable ignored) {
            // Keep fallback.
        }
        return fallback;
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        try {
            java.lang.management.ThreadMXBean raw = ManagementFactory.getThreadMXBean();
            if (raw instanceof com.sun.management.ThreadMXBean bean
                    && bean.isThreadAllocatedMemorySupported()) {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                return bean;
            }
        } catch (Throwable ignored) {
            // Allocation telemetry is optional.
        }
        return null;
    }

    private static long allocatedBytes() {
        try {
            return ALLOCATION_BEAN == null ? -1L
                    : ALLOCATION_BEAN.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static long allocationDelta(long before, long after) {
        return before < 0L || after < before ? -1L : after - before;
    }

    private static String marketId(MarketAPI market) {
        if (market == null) return "";
        try {
            String id = market.getId();
            return id == null ? "" : id;
        } catch (Throwable ignored) {
            return "<unavailable>";
        }
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "unspecified";
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static String csv(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String format(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (Double.isInfinite(value)) return value > 0d ? "Infinity" : "-Infinity";
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static boolean same(double left, double right) {
        if (Double.doubleToLongBits(left) == Double.doubleToLongBits(right)) return true;
        if (Double.isNaN(left) || Double.isNaN(right)) return false;
        return Math.abs(left - right) <= 0.00001d;
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope();

        private final String phase;
        private final String marketId;
        private final String detail;
        private final String threadName;
        private final boolean workerThread;
        private final long startedNanos;
        private final long startedAllocated;
        private final MarketSnapshot before;
        private boolean failed;
        private boolean closed;

        private Scope() {
            phase = "";
            marketId = "";
            detail = "";
            threadName = "";
            workerThread = false;
            startedNanos = 0L;
            startedAllocated = -1L;
            before = null;
            closed = true;
        }

        private Scope(String phase, String marketId, String detail,
                      long startedNanos, long startedAllocated, MarketSnapshot before) {
            this.phase = phase == null ? "" : phase;
            this.marketId = marketId == null ? "" : marketId;
            this.detail = detail == null ? "" : detail;
            this.threadName = Thread.currentThread().getName();
            this.workerThread = threadName.startsWith("AoTD Worker");
            this.startedNanos = startedNanos;
            this.startedAllocated = startedAllocated;
            this.before = before;
        }

        public void failed() {
            failed = true;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                finish(this);
            } catch (Throwable ignored) {
                disableBestEffort();
            }
        }
    }

    private static final class MarketSnapshot {
        final String marketId;
        final String factionId;
        final String econGroup;
        final int industryCount;
        final int commodityCount;
        final int pendingIndustries;
        final int disruptedIndustries;
        final int buildingIndustries;
        final int upgradingIndustries;
        final double rawSupply;
        final double rawDemand;
        final double stockpile;
        final double deficit;
        final double excess;
        final int valueCount;
        final long fingerprint;
        final LinkedHashMap<String, Double> values;

        private MarketSnapshot(String marketId, String factionId, String econGroup,
                               int industryCount, int commodityCount, int pendingIndustries,
                               int disruptedIndustries, int buildingIndustries,
                               int upgradingIndustries, double rawSupply, double rawDemand,
                               double stockpile, double deficit, double excess,
                               LinkedHashMap<String, Double> values) {
            this.marketId = marketId;
            this.factionId = factionId;
            this.econGroup = econGroup;
            this.industryCount = industryCount;
            this.commodityCount = commodityCount;
            this.pendingIndustries = pendingIndustries;
            this.disruptedIndustries = disruptedIndustries;
            this.buildingIndustries = buildingIndustries;
            this.upgradingIndustries = upgradingIndustries;
            this.rawSupply = rawSupply;
            this.rawDemand = rawDemand;
            this.stockpile = stockpile;
            this.deficit = deficit;
            this.excess = excess;
            this.values = values;
            this.valueCount = values.size();
            this.fingerprint = fingerprint(values);
        }

        static MarketSnapshot captureById(String marketId) {
            try {
                if (Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
                MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
                return market == null ? null : capture(market);
            } catch (Throwable ignored) {
                return null;
            }
        }

        static MarketSnapshot capture(MarketAPI market) {
            String id = marketId(market);
            String faction = "";
            String group = "";
            int pending = 0;
            int disrupted = 0;
            int building = 0;
            int upgrading = 0;
            double rawSupply = 0d;
            double rawDemand = 0d;
            double stockpile = 0d;
            double deficit = 0d;
            double excess = 0d;
            LinkedHashMap<String, Double> values = new LinkedHashMap<>();
            List<Industry> industries = new ArrayList<>();
            List<CommodityOnMarketAPI> commodities = new ArrayList<>();

            if (market != null) {
                try { faction = nullToEmpty(market.getFactionId()); } catch (Throwable ignored) {}
                try { group = nullToEmpty(market.getEconGroup()); } catch (Throwable ignored) {}
                try { industries.addAll(market.getIndustries()); } catch (Throwable ignored) {}
                try { commodities.addAll(market.getAllCommodities()); } catch (Throwable ignored) {}
            }

            industries.sort(Comparator.comparing(industry -> {
                try { return nullToEmpty(industry.getId()); }
                catch (Throwable ignored) { return ""; }
            }));
            commodities.sort(Comparator.comparing(commodity -> {
                try { return nullToEmpty(commodity.getId()); }
                catch (Throwable ignored) { return ""; }
            }));

            AoTDIndustryData industryData = null;
            try { industryData = AoTDIndustryData.getInstance(market); } catch (Throwable ignored) {}

            for (Industry industry : industries) {
                String industryId;
                try { industryId = nullToEmpty(industry.getId()); }
                catch (Throwable ignored) { industryId = "<unknown>"; }
                boolean isPending = false;
                try { isPending = industryData != null && industryData.isPending(industryId); }
                catch (Throwable ignored) {}
                if (isPending) pending++;
                try { if (industry.isDisrupted()) disrupted++; } catch (Throwable ignored) {}
                try { if (industry.isBuilding()) building++; } catch (Throwable ignored) {}
                try { if (industry.isUpgrading()) upgrading++; } catch (Throwable ignored) {}
                values.put("industry/" + industryId + "/pending", isPending ? 1d : 0d);

                for (CommodityOnMarketAPI commodity : commodities) {
                    String commodityId;
                    try { commodityId = nullToEmpty(commodity.getId()); }
                    catch (Throwable ignored) { continue; }
                    try {
                        values.put("industry/" + industryId + "/" + commodityId + "/supply",
                                (double) industry.getSupply(commodityId).getQuantity().getModifiedValue());
                    } catch (Throwable ignored) {}
                    try {
                        values.put("industry/" + industryId + "/" + commodityId + "/demand",
                                (double) industry.getDemand(commodityId).getQuantity().getModifiedValue());
                    } catch (Throwable ignored) {}
                }
            }

            for (CommodityOnMarketAPI commodity : commodities) {
                String commodityId;
                try { commodityId = nullToEmpty(commodity.getId()); }
                catch (Throwable ignored) { continue; }
                try {
                    values.put("commodity/" + commodityId + "/stockpile",
                            (double) commodity.getStockpile());
                    stockpile += commodity.getStockpile();
                } catch (Throwable ignored) {}
                if (commodity instanceof AoTDCommodityOnMarket aotd) {
                    try {
                        int value = aotd.getSupplyDemandData().getTotalRawUnitsFromSupply();
                        values.put("commodity/" + commodityId + "/rawSupply", (double) value);
                        rawSupply += value;
                    } catch (Throwable ignored) {}
                    try {
                        int value = aotd.getSupplyDemandData().getTotalRawUnitsFromDemand();
                        values.put("commodity/" + commodityId + "/rawDemand", (double) value);
                        rawDemand += value;
                    } catch (Throwable ignored) {}
                    try {
                        float value = aotd.getDeficitQuantity();
                        values.put("commodity/" + commodityId + "/deficit", (double) value);
                        deficit += value;
                    } catch (Throwable ignored) {}
                    try {
                        float value = aotd.getExcessQuantity();
                        values.put("commodity/" + commodityId + "/excess", (double) value);
                        excess += value;
                    } catch (Throwable ignored) {}
                    try { values.put("commodity/" + commodityId + "/monthlyDef", (double) aotd.getDef()); }
                    catch (Throwable ignored) {}
                    try { values.put("commodity/" + commodityId + "/monthlyExc", (double) aotd.getExc()); }
                    catch (Throwable ignored) {}
                }
            }

            return new MarketSnapshot(id, faction, group, industries.size(), commodities.size(),
                    pending, disrupted, building, upgrading, rawSupply, rawDemand,
                    stockpile, deficit, excess, values);
        }

        private static long fingerprint(LinkedHashMap<String, Double> values) {
            long hash = 0xcbf29ce484222325L;
            for (Map.Entry<String, Double> entry : values.entrySet()) {
                hash = fnv(hash, entry.getKey());
                long bits = Double.doubleToLongBits(entry.getValue());
                for (int i = 0; i < 8; i++) {
                    hash ^= bits & 0xffL;
                    hash *= 0x100000001b3L;
                    bits >>>= 8;
                }
            }
            return hash;
        }

        private static long fnv(long hash, String value) {
            if (value == null) return hash;
            for (int i = 0; i < value.length(); i++) {
                hash ^= value.charAt(i);
                hash *= 0x100000001b3L;
            }
            return hash;
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class PhaseStats {
        long calls;
        long failures;
        long workerCalls;
        long nonWorkerCalls;
        long totalNanos;
        long maxNanos;
        long allocatedSamples;
        long totalAllocatedBytes;
        long maxAllocatedBytes;
    }

    private record EventRow(long sequence, long revision, String phase, String marketId,
                            String detail, String thread, long durationNanos,
                            long allocatedBytes, String outcome) {}

    private record SnapshotRow(long sequence, long revision, String phase, String moment,
                               String marketId, String factionId, String econGroup,
                               int industries, int commodities, int pendingIndustries,
                               int disruptedIndustries, int buildingIndustries,
                               int upgradingIndustries, double rawSupply, double rawDemand,
                               double stockpile, double deficit, double excess,
                               int valueCount, long fingerprint) {}

    private record DiffRow(long sequence, long revision, String phase, String marketId,
                           String key, double beforeValue, double afterValue) {}

    private record TradeSnapshotRow(long sequence, long revision, String phase,
                                    String marketId, String factionId, String commodityId,
                                    int netProduction, int remainingNet,
                                    int internalSent, int internalReceived,
                                    float weight, float outsideWeight) {}
}
