package data.kaysaar.aotd.tot.compat;

/**
 * Loader-local state for one AoTD market revision.
 *
 * <p>The object deliberately stores the market as {@link Object}; this keeps the
 * registry ABI independent from Starsector classes and lets the javaagent call
 * it through the same mod class loader without reflection.</p>
 */
public final class MarketEconomyState {
    private final String marketId;
    private Object market;
    private long deliveredGeneration;
    private long structuralGeneration;
    private long derivedGeneration;
    private long priceGeneration;
    private long dirtyGeneration;
    private long lastDeliverySequence;
    private long lastSourceGeneration;
    private long firstDirtyNanos;
    private long lastDirtyNanos;
    private long lastComputeNanos;
    private int dirtyMask;
    private int priorityHint;
    private boolean queued;
    private boolean urgentQueued;
    private boolean snapshotBuilding;
    private boolean running;
    private boolean resultReady;
    private long failureGeneration;
    private int failureCount;
    private boolean quarantined;
    private String lastFailure;

    MarketEconomyState(String marketId, Object market) {
        this.marketId = marketId;
        this.market = market;
    }

    public String getMarketId() { return marketId; }
    public Object getMarket() { return market; }
    public long getDeliveredGeneration() { return deliveredGeneration; }
    public long getStructuralGeneration() { return structuralGeneration; }
    public long getDerivedGeneration() { return derivedGeneration; }
    public long getPriceGeneration() { return priceGeneration; }
    public long getDirtyGeneration() { return dirtyGeneration; }
    public long getLastDeliverySequence() { return lastDeliverySequence; }
    public long getLastSourceGeneration() { return lastSourceGeneration; }
    public long getFirstDirtyNanos() { return firstDirtyNanos; }
    public long getLastDirtyNanos() { return lastDirtyNanos; }
    public long getLastComputeNanos() { return lastComputeNanos; }
    public int getDirtyMask() { return dirtyMask; }
    public int getPriorityHint() { return priorityHint; }
    public boolean isQueued() { return queued; }
    public boolean isSnapshotBuilding() { return snapshotBuilding; }
    public boolean isRunning() { return running; }
    public boolean isResultReady() { return resultReady; }
    public long getFailureGeneration() { return failureGeneration; }
    public int getFailureCount() { return failureCount; }
    public boolean isQuarantined() { return quarantined; }
    public String getLastFailure() { return lastFailure; }

    void setMarket(Object market) { this.market = market; }
    void setDeliveredGeneration(long value) { deliveredGeneration = value; }
    void setStructuralGeneration(long value) { structuralGeneration = value; }
    void setDerivedGeneration(long value) { derivedGeneration = value; }
    void setPriceGeneration(long value) { priceGeneration = value; }
    void setDirtyGeneration(long value) { dirtyGeneration = value; }
    void setLastDeliverySequence(long value) { lastDeliverySequence = value; }
    void setLastSourceGeneration(long value) { lastSourceGeneration = value; }
    void setFirstDirtyNanos(long value) { firstDirtyNanos = value; }
    void setLastDirtyNanos(long value) { lastDirtyNanos = value; }
    void setLastComputeNanos(long value) { lastComputeNanos = value; }
    void setDirtyMask(int value) { dirtyMask = value; }
    void setPriorityHint(int value) { priorityHint = value; }
    void setQueued(boolean value) { queued = value; }
    boolean isUrgentQueued() { return urgentQueued; }
    void setUrgentQueued(boolean value) { urgentQueued = value; }
    void setSnapshotBuilding(boolean value) { snapshotBuilding = value; }
    void setRunning(boolean value) { running = value; }
    void setResultReady(boolean value) { resultReady = value; }
    void setFailureGeneration(long value) { failureGeneration = value; }
    void setFailureCount(int value) { failureCount = value; }
    void setQuarantined(boolean value) { quarantined = value; }
    void setLastFailure(String value) { lastFailure = value; }
}
