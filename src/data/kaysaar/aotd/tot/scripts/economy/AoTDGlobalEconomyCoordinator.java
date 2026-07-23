package data.kaysaar.aotd.tot.scripts.economy;

import data.kaysaar.aotd.tot.compat.MarketRegistry;
import data.kaysaar.aotd.tot.compat.SchedulerBridge;
import data.kaysaar.aotd.tot.scripts.trade.manager.AoTDTradeManager;

/** Coordinates temporal barriers and immutable global cuts over committed market snapshots. */
public final class AoTDGlobalEconomyCoordinator {
    public static final int BOUNDARY_INTERNAL_TRADE = 1;
    public static final int BOUNDARY_MONTH_END = 1 << 1;
    public static final int BOUNDARY_SAVE = 1 << 2;

    private static long localGlobalRevision;

    private AoTDGlobalEconomyCoordinator() {}

    /**
     * Delivers pending Prepatcher market time without opening a trade cut.
     * The resulting delivery generations are marked dirty and must be locally
     * recomputed before a hard global settlement is opened.
     */
    public static void flushDeliveredTimeForBoundary(int reasonMask) {
        long runtimeToken = SchedulerBridge.beforeGlobalBoundary(reasonMask, true);
        try {
            MarketRegistry.resynchronizeRuntimeGenerations();
        } finally {
            SchedulerBridge.afterGlobalBoundary(runtimeToken, localGlobalRevision);
        }
    }

    public static Boundary beginCommittedCut(int reasonMask, boolean hardFlush) {
        long runtimeToken = SchedulerBridge.beforeGlobalBoundary(reasonMask, hardFlush);
        try {
            if (hardFlush) MarketRegistry.resynchronizeRuntimeGenerations();
            AoTDTradeManager.CommittedCut cut =
                    AoTDTradeManager.getInstance().beginCommittedCut(reasonMask);
            long revision = nextPositive(localGlobalRevision);
            localGlobalRevision = revision;
            return new Boundary(runtimeToken, revision, cut);
        } catch (RuntimeException | Error failure) {
            SchedulerBridge.afterGlobalBoundary(runtimeToken, localGlobalRevision);
            throw failure;
        }
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    public static final class Boundary implements AutoCloseable {
        public final long runtimeToken;
        public final long revision;
        public final AoTDTradeManager.CommittedCut cut;
        private boolean closed;

        private Boundary(long runtimeToken, long revision, AoTDTradeManager.CommittedCut cut) {
            this.runtimeToken = runtimeToken;
            this.revision = revision;
            this.cut = cut;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                AoTDTradeManager.getInstance().endCommittedCut(cut);
            } finally {
                SchedulerBridge.afterGlobalBoundary(runtimeToken, revision);
            }
        }
    }
}
