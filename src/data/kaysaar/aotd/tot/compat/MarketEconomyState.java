package data.kaysaar.aotd.tot.compat;

/**
 * Loader-local state for one AoTD market revision.
 *
 * <p>The dirty mask answers which work is queued. The domain revision vector answers whether a
 * captured result is still causally valid. Keeping these two responsibilities separate prevents
 * unrelated trade/accessibility events from invalidating price work.
 */
public final class MarketEconomyState {
    private final String marketId;
    private Object market;

    /** Loader/prepatcher generations retained for diagnostics and compatibility. */
    private long deliveredGeneration;

    private long structuralGeneration;
    private long derivedGeneration;
    private long priceGeneration;
    private long dirtyGeneration;

    /** Current domain-specific input revisions. */
    private long structureRevision;

    private long materializedRevision;
    private long priceInputRevision;
    private long stockpileRevision;
    private long accessibilityRevision;
    private long tradeInputRevision;
    private long temporalRevision;

    /** Last successfully published revision vector per output domain. */
    private long materializedCommittedStructureRevision;

    private long materializedCommittedRevision;
    private long materializedCommittedTemporalRevision;

    private long priceCommittedStructureRevision;
    private long priceCommittedMaterializedRevision;
    private long priceCommittedInputRevision;
    private long priceCommittedStockpileRevision;
    private long priceCommittedTemporalRevision;

    private long tradeCommittedStructureRevision;
    private long tradeCommittedMaterializedRevision;
    private long tradeCommittedAccessibilityRevision;
    private long tradeCommittedInputRevision;
    private long tradeCommittedTemporalRevision;

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

    public String getMarketId() {
        return marketId;
    }

    public Object getMarket() {
        return market;
    }

    public long getDeliveredGeneration() {
        return deliveredGeneration;
    }

    public long getStructuralGeneration() {
        return structuralGeneration;
    }

    public long getDerivedGeneration() {
        return derivedGeneration;
    }

    public long getPriceGeneration() {
        return priceGeneration;
    }

    public long getDirtyGeneration() {
        return dirtyGeneration;
    }

    public long getStructureRevision() {
        return structureRevision;
    }

    public long getMaterializedRevision() {
        return materializedRevision;
    }

    public long getPriceInputRevision() {
        return priceInputRevision;
    }

    public long getStockpileRevision() {
        return stockpileRevision;
    }

    public long getAccessibilityRevision() {
        return accessibilityRevision;
    }

    public long getTradeInputRevision() {
        return tradeInputRevision;
    }

    public long getTemporalRevision() {
        return temporalRevision;
    }

    public long getMaterializedCommittedStructureRevision() {
        return materializedCommittedStructureRevision;
    }

    public long getMaterializedCommittedRevision() {
        return materializedCommittedRevision;
    }

    public long getMaterializedCommittedTemporalRevision() {
        return materializedCommittedTemporalRevision;
    }

    public long getPriceCommittedStructureRevision() {
        return priceCommittedStructureRevision;
    }

    public long getPriceCommittedMaterializedRevision() {
        return priceCommittedMaterializedRevision;
    }

    public long getPriceCommittedInputRevision() {
        return priceCommittedInputRevision;
    }

    public long getPriceCommittedStockpileRevision() {
        return priceCommittedStockpileRevision;
    }

    public long getPriceCommittedTemporalRevision() {
        return priceCommittedTemporalRevision;
    }

    public long getTradeCommittedStructureRevision() {
        return tradeCommittedStructureRevision;
    }

    public long getTradeCommittedMaterializedRevision() {
        return tradeCommittedMaterializedRevision;
    }

    public long getTradeCommittedAccessibilityRevision() {
        return tradeCommittedAccessibilityRevision;
    }

    public long getTradeCommittedInputRevision() {
        return tradeCommittedInputRevision;
    }

    public long getTradeCommittedTemporalRevision() {
        return tradeCommittedTemporalRevision;
    }

    public long getLastDeliverySequence() {
        return lastDeliverySequence;
    }

    public long getLastSourceGeneration() {
        return lastSourceGeneration;
    }

    public long getFirstDirtyNanos() {
        return firstDirtyNanos;
    }

    public long getLastDirtyNanos() {
        return lastDirtyNanos;
    }

    public long getLastComputeNanos() {
        return lastComputeNanos;
    }

    public int getDirtyMask() {
        return dirtyMask;
    }

    public int getPriorityHint() {
        return priorityHint;
    }

    public boolean isQueued() {
        return queued;
    }

    public boolean isSnapshotBuilding() {
        return snapshotBuilding;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isResultReady() {
        return resultReady;
    }

    public long getFailureGeneration() {
        return failureGeneration;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public String getLastFailure() {
        return lastFailure;
    }

    void setMarket(Object market) {
        this.market = market;
    }

    void setDeliveredGeneration(long value) {
        deliveredGeneration = value;
    }

    void setStructuralGeneration(long value) {
        structuralGeneration = value;
    }

    void setDerivedGeneration(long value) {
        derivedGeneration = value;
    }

    void setPriceGeneration(long value) {
        priceGeneration = value;
    }

    void setDirtyGeneration(long value) {
        dirtyGeneration = value;
    }

    void setStructureRevision(long value) {
        structureRevision = value;
    }

    void setMaterializedRevision(long value) {
        materializedRevision = value;
    }

    void setPriceInputRevision(long value) {
        priceInputRevision = value;
    }

    void setStockpileRevision(long value) {
        stockpileRevision = value;
    }

    void setAccessibilityRevision(long value) {
        accessibilityRevision = value;
    }

    void setTradeInputRevision(long value) {
        tradeInputRevision = value;
    }

    void setTemporalRevision(long value) {
        temporalRevision = value;
    }

    void commitMaterializedVector() {
        materializedCommittedStructureRevision = structureRevision;
        materializedCommittedRevision = materializedRevision;
        materializedCommittedTemporalRevision = temporalRevision;
    }

    void commitPriceVector() {
        priceCommittedStructureRevision = structureRevision;
        priceCommittedMaterializedRevision = materializedRevision;
        priceCommittedInputRevision = priceInputRevision;
        priceCommittedStockpileRevision = stockpileRevision;
        priceCommittedTemporalRevision = temporalRevision;
    }

    void commitTradeVector() {
        tradeCommittedStructureRevision = structureRevision;
        tradeCommittedMaterializedRevision = materializedRevision;
        tradeCommittedAccessibilityRevision = accessibilityRevision;
        tradeCommittedInputRevision = tradeInputRevision;
        tradeCommittedTemporalRevision = temporalRevision;
    }

    void setLastDeliverySequence(long value) {
        lastDeliverySequence = value;
    }

    void setLastSourceGeneration(long value) {
        lastSourceGeneration = value;
    }

    void setFirstDirtyNanos(long value) {
        firstDirtyNanos = value;
    }

    void setLastDirtyNanos(long value) {
        lastDirtyNanos = value;
    }

    void setLastComputeNanos(long value) {
        lastComputeNanos = value;
    }

    void setDirtyMask(int value) {
        dirtyMask = value;
    }

    void setPriorityHint(int value) {
        priorityHint = value;
    }

    void setQueued(boolean value) {
        queued = value;
    }

    boolean isUrgentQueued() {
        return urgentQueued;
    }

    void setUrgentQueued(boolean value) {
        urgentQueued = value;
    }

    void setSnapshotBuilding(boolean value) {
        snapshotBuilding = value;
    }

    void setRunning(boolean value) {
        running = value;
    }

    void setResultReady(boolean value) {
        resultReady = value;
    }

    void setFailureGeneration(long value) {
        failureGeneration = value;
    }

    void setFailureCount(int value) {
        failureCount = value;
    }

    void setQuarantined(boolean value) {
        quarantined = value;
    }

    void setLastFailure(String value) {
        lastFailure = value;
    }
}
