package data.kaysaar.aotd.tot.scripts.economy;

import data.kaysaar.aotd.tot.compat.SchedulerBridge;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Process-local identity of the currently installed campaign/economy runtime.
 *
 * <p>Domain revisions validate causality inside one economy. This epoch stamp
 * additionally proves that a batch belongs to the currently installed campaign
 * and Economy instance. Epochs are deliberately not persisted in campaign data.</p>
 */
public final class AoTDRuntimeEpoch {
    private static final Object LOCK = new Object();

    private static volatile long campaignEpoch = 1L;
    private static volatile long economyEpoch = 1L;
    private static long batchRevision;
    private static volatile Object economyIdentity;
    private static volatile String lastReason = "bootstrap";

    private AoTDRuntimeEpoch() {}

    public static Stamp captureBatch(String purpose) {
        synchronized (LOCK) {
            batchRevision = nextPositive(batchRevision);
            return new Stamp(campaignEpoch, economyEpoch, batchRevision,
                    purpose == null ? "unspecified" : purpose);
        }
    }

    public static EpochSnapshot snapshot() {
        synchronized (LOCK) {
            return new EpochSnapshot(campaignEpoch, economyEpoch, batchRevision,
                    economyIdentity, lastReason);
        }
    }

    public static boolean isCurrent(Stamp stamp) {
        return stamp != null
                && stamp.campaignEpoch == campaignEpoch
                && stamp.economyEpoch == economyEpoch;
    }

    static EpochSnapshot advanceCampaign(Object economy, String reason) {
        synchronized (LOCK) {
            campaignEpoch = nextPositive(campaignEpoch);
            economyEpoch = nextPositive(economyEpoch);
            economyIdentity = economy;
            lastReason = normalizeReason(reason, "campaign");
            publishToPrepatcherLocked();
            return snapshotLocked();
        }
    }

    static EpochSnapshot advanceEconomy(Object economy, String reason) {
        synchronized (LOCK) {
            economyEpoch = nextPositive(economyEpoch);
            economyIdentity = economy;
            lastReason = normalizeReason(reason, "economy");
            publishToPrepatcherLocked();
            return snapshotLocked();
        }
    }

    static EpochSnapshot bindLoadedEconomy(Object economy, String reason) {
        synchronized (LOCK) {
            economyIdentity = economy;
            lastReason = normalizeReason(reason, "bind-loaded-economy");
            publishToPrepatcherLocked();
            return snapshotLocked();
        }
    }

    static EpochSnapshot advanceShutdown(String reason) {
        synchronized (LOCK) {
            campaignEpoch = nextPositive(campaignEpoch);
            economyEpoch = nextPositive(economyEpoch);
            economyIdentity = null;
            lastReason = normalizeReason(reason, "shutdown");
            publishToPrepatcherLocked();
            return snapshotLocked();
        }
    }

    public static long getCampaignEpoch() { return campaignEpoch; }
    public static long getEconomyEpoch() { return economyEpoch; }

    public static boolean isCurrentEconomy(Object economy) {
        return economy != null && economyIdentity == economy;
    }

    public static String statusSummary() {
        EpochSnapshot snapshot = snapshot();
        return "campaignEpoch=" + snapshot.campaignEpoch
                + ", economyEpoch=" + snapshot.economyEpoch
                + ", batchRevision=" + snapshot.lastBatchRevision
                + ", economyIdentity=" + identity(snapshot.economyIdentity)
                + ", reason=" + snapshot.reason;
    }

    private static void publishToPrepatcherLocked() {
        SchedulerBridge.publishRuntimeEpoch(campaignEpoch, economyEpoch);
    }

    private static EpochSnapshot snapshotLocked() {
        return new EpochSnapshot(campaignEpoch, economyEpoch, batchRevision,
                economyIdentity, lastReason);
    }

    private static long nextPositive(long value) {
        long next = value + 1L;
        return next <= 0L ? 1L : next;
    }

    private static String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private static String identity(Object value) {
        if (value == null) return "null";
        return value.getClass().getName() + '@'
                + Integer.toHexString(System.identityHashCode(value));
    }

    public static final class Stamp implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final long campaignEpoch;
        public final long economyEpoch;
        public final long batchRevision;
        public final String purpose;

        Stamp(long campaignEpoch, long economyEpoch, long batchRevision, String purpose) {
            this.campaignEpoch = campaignEpoch;
            this.economyEpoch = economyEpoch;
            this.batchRevision = batchRevision;
            this.purpose = purpose;
        }

        public boolean isCurrent() { return AoTDRuntimeEpoch.isCurrent(this); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Stamp stamp)) return false;
            return campaignEpoch == stamp.campaignEpoch
                    && economyEpoch == stamp.economyEpoch
                    && batchRevision == stamp.batchRevision;
        }

        @Override
        public int hashCode() {
            return Objects.hash(campaignEpoch, economyEpoch, batchRevision);
        }

        @Override
        public String toString() {
            return "{campaign=" + campaignEpoch + ", economy=" + economyEpoch
                    + ", batch=" + batchRevision + ", purpose=" + purpose + '}';
        }
    }

    public static final class EpochSnapshot {
        public final long campaignEpoch;
        public final long economyEpoch;
        public final long lastBatchRevision;
        public final Object economyIdentity;
        public final String reason;

        EpochSnapshot(long campaignEpoch, long economyEpoch, long lastBatchRevision,
                      Object economyIdentity, String reason) {
            this.campaignEpoch = campaignEpoch;
            this.economyEpoch = economyEpoch;
            this.lastBatchRevision = lastBatchRevision;
            this.economyIdentity = economyIdentity;
            this.reason = reason;
        }
    }
}
