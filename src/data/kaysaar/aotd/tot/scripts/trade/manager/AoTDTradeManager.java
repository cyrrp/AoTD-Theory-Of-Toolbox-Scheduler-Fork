package data.kaysaar.aotd.tot.scripts.trade.manager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import data.kaysaar.aotd.tot.listeners.AoTDCoreUIListener;
import data.kaysaar.aotd.tot.misc.AoTDToolboxMisc;
import data.kaysaar.aotd.tot.scripts.commoditydata.AoTDCommodityOnMarket;
import data.kaysaar.aotd.tot.scripts.economy.AoTDEconomySemanticBaseline;
import data.kaysaar.aotd.tot.scripts.economy.AoTDRuntimeEpoch;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDFactionTradeData;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDInternalTradeBatch;
import data.kaysaar.aotd.tot.scripts.trade.models.AoTDMarketData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class AoTDTradeManager {
    public static String memkey = "$aotd_trade_manager";
    public static boolean endOfMonth = false;
    LinkedHashMap<String, AoTDFactionTradeData> factionsTradeData = new LinkedHashMap<>();
    LinkedHashSet<String> possibleCommoditiesDemanded = new LinkedHashSet<>();
    /** Authoritative owner of each published market snapshot. */
    LinkedHashMap<String, String> marketFactionById = new LinkedHashMap<>();
    public static float multFromSellingExcess = 0.01f;

    private long localPublicationRevision;
    private long settlementSequence;
    private transient boolean settlementOpen;
    private transient long activeSettlementToken;
    private transient LinkedHashMap<String, PendingSnapshot> pendingSnapshots;
    private transient LinkedHashSet<String> pendingRemovals;

    private Object readResolve() {
        if (factionsTradeData == null) factionsTradeData = new LinkedHashMap<>();
        if (possibleCommoditiesDemanded == null) possibleCommoditiesDemanded = new LinkedHashSet<>();
        if (marketFactionById == null) marketFactionById = new LinkedHashMap<>();
        ensureTransientState();
        if (marketFactionById.isEmpty()) {
            for (Map.Entry<String, AoTDFactionTradeData> faction : factionsTradeData.entrySet()) {
                for (String marketId : faction.getValue().getTradeData().keySet()) {
                    marketFactionById.put(marketId, faction.getKey());
                }
            }
        }
        return this;
    }

    private void ensureTransientState() {
        if (pendingSnapshots == null) pendingSnapshots = new LinkedHashMap<>();
        if (pendingRemovals == null) pendingRemovals = new LinkedHashSet<>();
    }

    public synchronized void recordPossibleCommodity(String commodityId) {
        if (commodityId != null) possibleCommoditiesDemanded.add(commodityId);
    }

    public synchronized LinkedHashSet<String> getPossibleCommoditiesDemandedOrSupplied() {
        return new LinkedHashSet<>(possibleCommoditiesDemanded);
    }

    public synchronized List<String> getPossibleCommoditiesDemandedOrSuppliedSorted(Comparator<String> comparator) {
        return possibleCommoditiesDemanded.stream().sorted(comparator).toList();
    }

    public static int getExportIncome(CommodityOnMarketAPI comOnMarket) {
        if (comOnMarket instanceof AoTDCommodityOnMarket commodity) {
            return AoTDCoreUIListener.isInCore()
                    ? AoTDToolboxMisc.getExpectedMonthlyIncomeFromCommodity(commodity)
                    : AoTDToolboxMisc.getIncomeFromSelling(commodity);
        }
        return comOnMarket.getExportIncome();
    }

    public static AoTDTradeManager getInstance() {
        if (!Global.getSector().getPersistentData().containsKey(memkey)) {
            Global.getSector().getPersistentData().put(memkey, new AoTDTradeManager());
        }
        AoTDTradeManager manager = (AoTDTradeManager) Global.getSector().getPersistentData().get(memkey);
        manager.ensureTransientState();
        return manager;
    }

    /** Invalidates an open cut without publishing deferred old-epoch changes. */
    public synchronized void invalidateRuntimeEpochState() {
        ensureTransientState();
        settlementOpen = false;
        activeSettlementToken = 0L;
        pendingSnapshots.clear();
        pendingRemovals.clear();
    }

    /** Best-effort invalidation of the manager installed in the current sector. */
    public static void invalidateInstalledRuntimeEpochState() {
        try {
            if (Global.getSector() == null) return;
            Object value = Global.getSector().getPersistentData().get(memkey);
            if (value instanceof AoTDTradeManager manager) {
                manager.invalidateRuntimeEpochState();
            }
        } catch (RuntimeException ignored) {
            // Campaign bootstrap/teardown may not expose a sector yet. Epoch checks
            // still make every old cut non-committable; this hook prevents a stuck
            // settlement when the same persistent manager survives economy replacement.
        }
    }

    /**
     * Captures the exact post-immigration trade inputs without publishing them.
     * A whole economy iteration can therefore validate every market before any
     * new trade snapshot becomes visible to the global cut.
     */
    public PreparedSnapshot preparePostImmigrationSnapshot(MarketAPI market) {
        if (market == null) return PreparedSnapshot.failed(null, "null-market");
        AoTDMarketData candidate;
        try (AoTDEconomySemanticBaseline.Scope ignored =
                     AoTDEconomySemanticBaseline.begin(
                             "trade-manager.capture-post-immigration", market,
                             market.getFactionId())) {
            candidate = AoTDMarketData.capturePostImmigration(market);
        } catch (RuntimeException failure) {
            return PreparedSnapshot.failed(market.getId(), failure.toString());
        }

        synchronized (this) {
            ensureTransientState();
            String marketId = market.getId();
            String nextFaction = market.getFactionId();
            if (marketId == null || nextFaction == null) {
                return PreparedSnapshot.failed(marketId, "missing-market-or-faction-id");
            }
            String previousFaction = marketFactionById.get(marketId);
            AoTDMarketData previous = findPublishedSnapshotLocked(previousFaction, marketId);

            int reasonMask = 0;
            if (previous == null) {
                reasonMask |= SnapshotRefreshResult.REASON_INITIAL;
            } else {
                int modelChanges = candidate.changeMaskComparedTo(previous);
                if ((modelChanges & AoTDMarketData.CHANGE_ACCESSIBILITY) != 0) {
                    reasonMask |= SnapshotRefreshResult.REASON_ACCESSIBILITY;
                }
                if ((modelChanges & AoTDMarketData.CHANGE_ELIGIBILITY) != 0) {
                    reasonMask |= SnapshotRefreshResult.REASON_ELIGIBILITY;
                }
                if ((modelChanges & AoTDMarketData.CHANGE_NET_PRODUCTION) != 0) {
                    reasonMask |= SnapshotRefreshResult.REASON_NET_PRODUCTION;
                }
            }
            if (previousFaction != null && !previousFaction.equals(nextFaction)) {
                reasonMask |= SnapshotRefreshResult.REASON_FACTION;
            }

            boolean sameFaction = previousFaction == null
                    ? previous == null
                    : previousFaction.equals(nextFaction);
            boolean changed = previous == null || !sameFaction
                    || !candidate.hasSameTradeInputs(previous);
            long expectedRevision = previous == null ? 0L : previous.publicationRevision;
            return PreparedSnapshot.ready(
                    marketId, nextFaction, previousFaction, expectedRevision,
                    candidate, changed, reasonMask);
        }
    }

    /**
     * Atomically validates and publishes a complete post-immigration snapshot
     * batch. No market is published if any prepared baseline became stale.
     */
    public synchronized boolean commitPreparedSnapshots(List<PreparedSnapshot> prepared) {
        ensureTransientState();
        if (prepared == null || settlementOpen) return false;
        for (PreparedSnapshot item : prepared) {
            if (item == null || item.failed || item.candidate == null) return false;
            String currentFaction = marketFactionById.get(item.marketId);
            AoTDMarketData current = findPublishedSnapshotLocked(currentFaction, item.marketId);
            long currentRevision = current == null ? 0L : current.publicationRevision;
            if (!sameNullable(currentFaction, item.expectedPreviousFaction)
                    || currentRevision != item.expectedPreviousRevision) {
                return false;
            }
        }
        for (PreparedSnapshot item : prepared) {
            if (!item.changed) continue;
            publishSnapshotLocked(item.nextFaction, item.candidate);
            item.committedPublicationRevision = item.candidate.publicationRevision;
        }
        return true;
    }

    /** Convenience compatibility wrapper for non-batched callers. */
    public SnapshotRefreshResult refreshMarketSnapshotIfChanged(MarketAPI market) {
        PreparedSnapshot prepared = preparePostImmigrationSnapshot(market);
        if (prepared.failed) {
            return SnapshotRefreshResult.failed(prepared.marketId, prepared.failure);
        }
        ArrayList<PreparedSnapshot> singleton = new ArrayList<>(1);
        singleton.add(prepared);
        if (!commitPreparedSnapshots(singleton)) {
            return SnapshotRefreshResult.failed(prepared.marketId, "stale-prepare-baseline");
        }
        if (!prepared.changed) {
            return SnapshotRefreshResult.unchanged(
                    prepared.marketId, prepared.candidate.tradeFingerprint,
                    prepared.expectedPreviousRevision);
        }
        return SnapshotRefreshResult.published(
                prepared.marketId, prepared.reasonMask, false,
                prepared.candidate.tradeFingerprint,
                prepared.committedPublicationRevision);
    }

    private AoTDMarketData findPublishedSnapshotLocked(String factionId, String marketId) {
        if (factionId == null || marketId == null) return null;
        AoTDFactionTradeData data = factionsTradeData.get(factionId);
        return data == null ? null : data.getTradeData().get(marketId);
    }

    private static boolean sameNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    /** Builds on campaign thread and publishes now or after the active committed cut. */
    public void addMarket(MarketAPI market) {
        if (market == null) return;
        try (AoTDEconomySemanticBaseline.Scope ignored =
                     AoTDEconomySemanticBaseline.begin(
                             "trade-manager.build-market-snapshot", market,
                             market.getFactionId())) {
            AoTDMarketData snapshot = new AoTDMarketData(market);
            synchronized (this) {
                ensureTransientState();
                if (settlementOpen) {
                    pendingRemovals.remove(market.getId());
                    pendingSnapshots.put(market.getId(),
                            new PendingSnapshot(market.getFactionId(), snapshot));
                    AoTDEconomySemanticBaseline.operation(
                            "trade-manager.snapshot-deferred-to-next-cut", market);
                } else {
                    publishSnapshotLocked(market.getFactionId(), snapshot);
                }
            }
            AoTDEconomySemanticBaseline.captureTradeSnapshot(
                    "trade-manager.build-market-snapshot", market);
        }
    }

    private void publishSnapshotLocked(String factionId, AoTDMarketData snapshot) {
        if (snapshot == null || snapshot.marketId == null || factionId == null) return;
        String previousFaction = marketFactionById.put(snapshot.marketId, factionId);
        if (previousFaction != null && !previousFaction.equals(factionId)) {
            AoTDFactionTradeData previous = factionsTradeData.get(previousFaction);
            if (previous != null) previous.removeMarketSnapshot(snapshot.marketId);
        }
        AoTDFactionTradeData factionData = factionsTradeData.computeIfAbsent(
                factionId, AoTDFactionTradeData::new);
        localPublicationRevision = nextPositive(localPublicationRevision);
        snapshot.publicationRevision = localPublicationRevision;
        factionData.putMarketSnapshot(snapshot);
    }

    /** Removes the published snapshot now or after the active cut. */
    public synchronized void removeMarket(MarketAPI market) {
        if (market == null) return;
        ensureTransientState();
        String marketId = market.getId();
        if (settlementOpen) {
            pendingSnapshots.remove(marketId);
            pendingRemovals.add(marketId);
            return;
        }
        removePublishedLocked(marketId);
    }

    private void removePublishedLocked(String marketId) {
        String factionId = marketFactionById.remove(marketId);
        if (factionId != null) {
            AoTDFactionTradeData data = factionsTradeData.get(factionId);
            if (data != null) data.removeMarketSnapshot(marketId);
            localPublicationRevision = nextPositive(localPublicationRevision);
            return;
        }
        // Restricted save-upgrade repair path.
        for (AoTDFactionTradeData data : factionsTradeData.values()) {
            if (data.removeMarketSnapshot(marketId)) {
                localPublicationRevision = nextPositive(localPublicationRevision);
                break;
            }
        }
    }

    /** Opens an immutable cut. New local publications are deferred to the next cut. */
    public synchronized CommittedCut beginCommittedCut(int reasonMask) {
        return beginCommittedCut(reasonMask,
                AoTDRuntimeEpoch.captureBatch("committed-cut"));
    }

    public synchronized CommittedCut beginCommittedCut(
            int reasonMask, AoTDRuntimeEpoch.Stamp epochStamp) {
        ensureTransientState();
        if (!AoTDRuntimeEpoch.isCurrent(epochStamp)) {
            throw new IllegalStateException("Cannot open stale AoTD committed cut: " + epochStamp);
        }
        if (settlementOpen) {
            throw new IllegalStateException("AoTD committed cut already open: token="
                    + activeSettlementToken);
        }
        settlementOpen = true;
        activeSettlementToken = nextPositive(settlementSequence);
        settlementSequence = activeSettlementToken;
        AoTDInternalTradeBatch batch = new AoTDInternalTradeBatch(epochStamp);
        for (Map.Entry<String, AoTDFactionTradeData> entry : factionsTradeData.entrySet()) {
            ArrayList<AoTDInternalTradeBatch.MarketInput> markets = new ArrayList<>();
            for (AoTDMarketData market : entry.getValue().getTradeData().values()) {
                markets.add(market.toInternalTradeInput());
            }
            batch.addFaction(new AoTDInternalTradeBatch.FactionInput(
                    entry.getKey(), markets.toArray(new AoTDInternalTradeBatch.MarketInput[0])));
        }
        batch.freeze();
        return new CommittedCut(activeSettlementToken, localPublicationRevision,
                reasonMask, batch, epochStamp);
    }

    /** Applies pure results to the exact snapshots protected by the open cut. */
    public synchronized boolean commitInternalTrade(
            CommittedCut cut, AoTDInternalTradeBatch batch) {
        if (!isActiveCutLocked(cut) || batch == null
                || !cut.epochStamp.equals(batch.epochStamp)) return false;
        for (int factionIndex = 0; factionIndex < batch.size(); factionIndex++) {
            AoTDInternalTradeBatch.FactionResult result = batch.resultAt(factionIndex);
            if (result == null || result.failure != null) return false;
            AoTDFactionTradeData faction = factionsTradeData.get(result.factionId);
            if (faction == null || result.markets == null) continue;
            for (AoTDInternalTradeBatch.MarketResult marketResult : result.markets) {
                AoTDMarketData market = faction.getTradeData().get(marketResult.marketId);
                if (market != null && market.publicationRevision <= cut.localPublicationRevision) {
                    market.applyInternalTradeResult(marketResult);
                }
            }
        }
        return true;
    }

    public synchronized void endCommittedCut(CommittedCut cut) {
        if (!isActiveCutLocked(cut)) return;
        settlementOpen = false;
        activeSettlementToken = 0L;
        for (String marketId : pendingRemovals) removePublishedLocked(marketId);
        pendingRemovals.clear();
        for (PendingSnapshot pending : pendingSnapshots.values()) {
            publishSnapshotLocked(pending.factionId, pending.snapshot);
        }
        pendingSnapshots.clear();
    }

    private boolean isActiveCutLocked(CommittedCut cut) {
        return cut != null
                && AoTDRuntimeEpoch.isCurrent(cut.epochStamp)
                && settlementOpen
                && activeSettlementToken == cut.token;
    }

    public synchronized long getLocalPublicationRevision() {
        return localPublicationRevision;
    }

    public synchronized boolean isSettlementOpen() { return settlementOpen; }

    public synchronized AoTDFactionTradeData getPlayerManager() {
        return factionsTradeData.get(Global.getSector().getPlayerFaction().getId());
    }

    public synchronized AoTDFactionTradeData getFactionTradeData(String factionId) {
        return factionsTradeData.computeIfAbsent(factionId, AoTDFactionTradeData::new);
    }

    public synchronized AoTDMarketData getMarketData(MarketAPI market) {
        if (market == null) return null;
        AoTDFactionTradeData data = factionsTradeData.get(market.getFactionId());
        return data == null ? null : data.getTradeData().get(market.getId());
    }

    /** Campaign-thread read view; callers must not mutate while a cut is open. */
    public synchronized LinkedHashMap<String, AoTDFactionTradeData> getAllFactionTradeData() {
        return factionsTradeData;
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    public static final class PreparedSnapshot {
        public final String marketId;
        public final String nextFaction;
        public final boolean changed;
        public final boolean failed;
        public final int reasonMask;
        public final long fingerprint;
        public final String failure;
        private final String expectedPreviousFaction;
        private final long expectedPreviousRevision;
        private final AoTDMarketData candidate;
        private long committedPublicationRevision;

        private PreparedSnapshot(
                String marketId, String nextFaction, String expectedPreviousFaction,
                long expectedPreviousRevision, AoTDMarketData candidate,
                boolean changed, boolean failed, int reasonMask, String failure) {
            this.marketId = marketId;
            this.nextFaction = nextFaction;
            this.expectedPreviousFaction = expectedPreviousFaction;
            this.expectedPreviousRevision = expectedPreviousRevision;
            this.candidate = candidate;
            this.changed = changed;
            this.failed = failed;
            this.reasonMask = reasonMask;
            this.fingerprint = candidate == null ? 0L : candidate.tradeFingerprint;
            this.failure = failure;
        }

        private static PreparedSnapshot ready(
                String marketId, String nextFaction, String expectedPreviousFaction,
                long expectedPreviousRevision, AoTDMarketData candidate,
                boolean changed, int reasonMask) {
            return new PreparedSnapshot(
                    marketId, nextFaction, expectedPreviousFaction,
                    expectedPreviousRevision, candidate, changed, false,
                    reasonMask, null);
        }

        private static PreparedSnapshot failed(String marketId, String failure) {
            return new PreparedSnapshot(
                    marketId, null, null, 0L, null, false, true, 0, failure);
        }

        public long getPublicationRevisionAfterCommit() {
            return changed ? committedPublicationRevision : expectedPreviousRevision;
        }
    }

    public static final class SnapshotRefreshResult {
        public static final int REASON_INITIAL = 1;
        public static final int REASON_FACTION = 1 << 1;
        public static final int REASON_ACCESSIBILITY = 1 << 2;
        public static final int REASON_ELIGIBILITY = 1 << 3;
        public static final int REASON_NET_PRODUCTION = 1 << 4;

        public final String marketId;
        public final boolean published;
        public final boolean deferred;
        public final boolean failed;
        public final int reasonMask;
        public final long fingerprint;
        public final long publicationRevision;
        public final String failure;

        private SnapshotRefreshResult(
                String marketId, boolean published, boolean deferred, boolean failed,
                int reasonMask, long fingerprint, long publicationRevision,
                String failure) {
            this.marketId = marketId;
            this.published = published;
            this.deferred = deferred;
            this.failed = failed;
            this.reasonMask = reasonMask;
            this.fingerprint = fingerprint;
            this.publicationRevision = publicationRevision;
            this.failure = failure;
        }

        private static SnapshotRefreshResult published(
                String marketId, int reasonMask, boolean deferred,
                long fingerprint, long publicationRevision) {
            return new SnapshotRefreshResult(
                    marketId, true, deferred, false, reasonMask, fingerprint,
                    publicationRevision, null);
        }

        private static SnapshotRefreshResult unchanged(
                String marketId, long fingerprint, long publicationRevision) {
            return new SnapshotRefreshResult(
                    marketId, false, false, false, 0, fingerprint,
                    publicationRevision, null);
        }

        private static SnapshotRefreshResult failed(String marketId, String failure) {
            return new SnapshotRefreshResult(
                    marketId, false, false, true, 0, 0L, 0L, failure);
        }
    }

    private static final class PendingSnapshot {
        final String factionId;
        final AoTDMarketData snapshot;
        PendingSnapshot(String factionId, AoTDMarketData snapshot) {
            this.factionId = factionId;
            this.snapshot = snapshot;
        }
    }

    public static final class CommittedCut {
        public final long token;
        public final long localPublicationRevision;
        public final int reasonMask;
        public final AoTDInternalTradeBatch internalTradeBatch;
        public final AoTDRuntimeEpoch.Stamp epochStamp;
        public final long campaignEpoch;
        public final long economyEpoch;
        public final long batchRevision;

        private CommittedCut(long token, long localPublicationRevision,
                             int reasonMask, AoTDInternalTradeBatch internalTradeBatch,
                             AoTDRuntimeEpoch.Stamp epochStamp) {
            this.token = token;
            this.localPublicationRevision = localPublicationRevision;
            this.reasonMask = reasonMask;
            this.internalTradeBatch = internalTradeBatch;
            this.epochStamp = epochStamp;
            this.campaignEpoch = epochStamp.campaignEpoch;
            this.economyEpoch = epochStamp.economyEpoch;
            this.batchRevision = epochStamp.batchRevision;
        }
    }
}
