package com.hgtech.soma.runtime;

/** Immutable self-consistent dense table stats snapshot。 */
public final class TableStats {
    private final String schemaHash;
    private final String runtimeCompatibility;
    private final String runtimePlanHash;
    private final StatsMode statsMode;
    private final int rows;
    private final int capacity;
    private final long structuralEpoch;
    private final boolean released;
    private final int activeViews;
    private final long growthCount;
    private final long updateScratchCurrentBytes;
    private final long updateScratchHighWaterBytes;
    private final long sidecarDirtyCount;
    private final long sidecarRebuildCount;
    private final long sidecarRebuildRows;
    private final long sidecarScratchCurrentBytes;
    private final long sidecarScratchHighWaterBytes;
    private final String lastOperation;
    private final OperationOutcome lastOutcome;
    private final String lastErrorCode;
    private final long lastScanned;
    private final long lastMatched;
    private final long lastChanged;
    private final long childInstanceCount;
    private final long descendantRowCount;
    private final long materializationInvocationCount;
    private final long materializationFailureCount;
    private final String lastMaterializationBudgetIdentity;
    private final int lastMaterializationMaximumOwnershipDepth;
    private final long lastMaterializationTableInstances;
    private final long lastMaterializationRows;
    private final long lastMaterializationLeafValues;
    private final long lastMaterializationEstimatedAllocationBytes;

    private TableStats(
            String schemaHash,
            String runtimeCompatibility,
            String runtimePlanHash,
            StatsMode statsMode,
            int rows,
            int capacity,
            long structuralEpoch,
            boolean released,
            int activeViews,
            long growthCount,
            long updateScratchCurrentBytes,
            long updateScratchHighWaterBytes,
            long sidecarDirtyCount,
            long sidecarRebuildCount,
            long sidecarRebuildRows,
            long sidecarScratchCurrentBytes,
            long sidecarScratchHighWaterBytes,
            String lastOperation,
            OperationOutcome lastOutcome,
            String lastErrorCode,
            long lastScanned,
            long lastMatched,
            long lastChanged,
            long childInstanceCount,
            long descendantRowCount,
            long materializationInvocationCount,
            long materializationFailureCount,
            String lastMaterializationBudgetIdentity,
            int lastMaterializationMaximumOwnershipDepth,
            long lastMaterializationTableInstances,
            long lastMaterializationRows,
            long lastMaterializationLeafValues,
            long lastMaterializationEstimatedAllocationBytes) {
        this.schemaHash = schemaHash;
        this.runtimeCompatibility = runtimeCompatibility;
        this.runtimePlanHash = runtimePlanHash;
        this.statsMode = statsMode;
        this.rows = rows;
        this.capacity = capacity;
        this.structuralEpoch = structuralEpoch;
        this.released = released;
        this.activeViews = activeViews;
        this.growthCount = growthCount;
        this.updateScratchCurrentBytes = updateScratchCurrentBytes;
        this.updateScratchHighWaterBytes = updateScratchHighWaterBytes;
        this.sidecarDirtyCount = sidecarDirtyCount;
        this.sidecarRebuildCount = sidecarRebuildCount;
        this.sidecarRebuildRows = sidecarRebuildRows;
        this.sidecarScratchCurrentBytes = sidecarScratchCurrentBytes;
        this.sidecarScratchHighWaterBytes = sidecarScratchHighWaterBytes;
        this.lastOperation = lastOperation;
        this.lastOutcome = lastOutcome;
        this.lastErrorCode = lastErrorCode;
        this.lastScanned = lastScanned;
        this.lastMatched = lastMatched;
        this.lastChanged = lastChanged;
        this.childInstanceCount = childInstanceCount;
        this.descendantRowCount = descendantRowCount;
        this.materializationInvocationCount = materializationInvocationCount;
        this.materializationFailureCount = materializationFailureCount;
        this.lastMaterializationBudgetIdentity = lastMaterializationBudgetIdentity;
        this.lastMaterializationMaximumOwnershipDepth = lastMaterializationMaximumOwnershipDepth;
        this.lastMaterializationTableInstances = lastMaterializationTableInstances;
        this.lastMaterializationRows = lastMaterializationRows;
        this.lastMaterializationLeafValues = lastMaterializationLeafValues;
        this.lastMaterializationEstimatedAllocationBytes = lastMaterializationEstimatedAllocationBytes;
    }

    public static TableStats create(
            String schemaHash,
            String runtimeCompatibility,
            String runtimePlanHash,
            StatsMode statsMode,
            int rows,
            int capacity,
            long structuralEpoch,
            boolean released,
            int activeViews,
            long growthCount,
            long updateScratchCurrentBytes,
            long updateScratchHighWaterBytes,
            long sidecarDirtyCount,
            long sidecarRebuildCount,
            long sidecarRebuildRows,
            String lastOperation,
            OperationOutcome lastOutcome,
            String lastErrorCode,
            long lastScanned,
            long lastMatched,
            long lastChanged) {
        return create(schemaHash, runtimeCompatibility, runtimePlanHash, statsMode,
                rows, capacity, structuralEpoch, released, activeViews, growthCount,
                updateScratchCurrentBytes, updateScratchHighWaterBytes,
                sidecarDirtyCount, sidecarRebuildCount, sidecarRebuildRows,
                0L, 0L, lastOperation, lastOutcome, lastErrorCode,
                lastScanned, lastMatched, lastChanged);
    }

    public static TableStats create(
            String schemaHash,
            String runtimeCompatibility,
            String runtimePlanHash,
            StatsMode statsMode,
            int rows,
            int capacity,
            long structuralEpoch,
            boolean released,
            int activeViews,
            long growthCount,
            long updateScratchCurrentBytes,
            long updateScratchHighWaterBytes,
            String lastOperation,
            OperationOutcome lastOutcome,
            String lastErrorCode,
            long lastScanned,
            long lastMatched,
            long lastChanged) {
        return create(schemaHash, runtimeCompatibility, runtimePlanHash, statsMode,
                rows, capacity, structuralEpoch, released, activeViews, growthCount,
                updateScratchCurrentBytes, updateScratchHighWaterBytes,
                0L, 0L, 0L, 0L, 0L,
                lastOperation, lastOutcome, lastErrorCode,
                lastScanned, lastMatched, lastChanged);
    }

    public static TableStats create(
            String schemaHash,
            String runtimeCompatibility,
            String runtimePlanHash,
            StatsMode statsMode,
            int rows,
            int capacity,
            long structuralEpoch,
            boolean released,
            int activeViews,
            long growthCount,
            long updateScratchCurrentBytes,
            long updateScratchHighWaterBytes,
            long sidecarDirtyCount,
            long sidecarRebuildCount,
            long sidecarRebuildRows,
            long sidecarScratchCurrentBytes,
            long sidecarScratchHighWaterBytes,
            String lastOperation,
            OperationOutcome lastOutcome,
            String lastErrorCode,
            long lastScanned,
            long lastMatched,
            long lastChanged) {
        CanonicalSupport.required(schemaHash, "schemaHash");
        CanonicalSupport.required(runtimeCompatibility, "runtimeCompatibility");
        CanonicalSupport.required(runtimePlanHash, "runtimePlanHash");
        if (statsMode == null) throw new NullPointerException("statsMode");
        if (lastOperation == null) throw new NullPointerException("lastOperation");
        if (lastOutcome == null) throw new NullPointerException("lastOutcome");
        if (lastErrorCode == null) throw new NullPointerException("lastErrorCode");
        if (rows < 0 || capacity < rows || activeViews < 0 || structuralEpoch < 0L
                || growthCount < 0L || updateScratchCurrentBytes < 0L
                || updateScratchHighWaterBytes < updateScratchCurrentBytes
                || sidecarDirtyCount < 0L || sidecarRebuildCount < 0L
                || sidecarRebuildRows < 0L
                || sidecarScratchCurrentBytes < 0L
                || sidecarScratchHighWaterBytes < sidecarScratchCurrentBytes
                || lastScanned < 0L || lastMatched < 0L || lastChanged < 0L
                || lastChanged > lastMatched || lastMatched > lastScanned) {
            throw new IllegalArgumentException("invalid table stats snapshot");
        }
        return new TableStats(schemaHash, runtimeCompatibility, runtimePlanHash,
                statsMode, rows, capacity, structuralEpoch, released, activeViews,
                growthCount, updateScratchCurrentBytes, updateScratchHighWaterBytes,
                sidecarDirtyCount, sidecarRebuildCount, sidecarRebuildRows,
                sidecarScratchCurrentBytes, sidecarScratchHighWaterBytes,
                lastOperation, lastOutcome, lastErrorCode,
                lastScanned, lastMatched, lastChanged,
                0L, 0L, 0L, 0L, "", 0, 0L, 0L, 0L, 0L);
    }

    public static TableStats withPhase4(
            TableStats base,
            long childInstanceCount,
            long descendantRowCount,
            long materializationInvocationCount,
            long materializationFailureCount,
            String lastMaterializationBudgetIdentity,
            int lastMaterializationMaximumOwnershipDepth,
            long lastMaterializationTableInstances,
            long lastMaterializationRows,
            long lastMaterializationLeafValues,
            long lastMaterializationEstimatedAllocationBytes) {
        if (base == null) throw new NullPointerException("base");
        if (lastMaterializationBudgetIdentity == null) {
            throw new NullPointerException("lastMaterializationBudgetIdentity");
        }
        if (childInstanceCount < 0L || descendantRowCount < 0L
                || materializationInvocationCount < 0L || materializationFailureCount < 0L
                || materializationFailureCount > materializationInvocationCount
                || lastMaterializationMaximumOwnershipDepth < 0
                || lastMaterializationTableInstances < 0L || lastMaterializationRows < 0L
                || lastMaterializationLeafValues < 0L
                || lastMaterializationEstimatedAllocationBytes < 0L) {
            throw new IllegalArgumentException("invalid phase4 table stats");
        }
        return new TableStats(base.schemaHash, base.runtimeCompatibility,
                base.runtimePlanHash, base.statsMode, base.rows, base.capacity,
                base.structuralEpoch, base.released, base.activeViews, base.growthCount,
                base.updateScratchCurrentBytes, base.updateScratchHighWaterBytes,
                base.sidecarDirtyCount, base.sidecarRebuildCount, base.sidecarRebuildRows,
                base.sidecarScratchCurrentBytes, base.sidecarScratchHighWaterBytes,
                base.lastOperation, base.lastOutcome, base.lastErrorCode,
                base.lastScanned, base.lastMatched, base.lastChanged,
                childInstanceCount, descendantRowCount,
                materializationInvocationCount, materializationFailureCount,
                lastMaterializationBudgetIdentity,
                lastMaterializationMaximumOwnershipDepth,
                lastMaterializationTableInstances, lastMaterializationRows,
                lastMaterializationLeafValues, lastMaterializationEstimatedAllocationBytes);
    }

    public String schemaHash() { return schemaHash; }
    public String runtimeCompatibility() { return runtimeCompatibility; }
    public String runtimePlanHash() { return runtimePlanHash; }
    public StatsMode statsMode() { return statsMode; }
    public int rows() { return rows; }
    public int capacity() { return capacity; }
    public long structuralEpoch() { return structuralEpoch; }
    public boolean released() { return released; }
    public int activeViews() { return activeViews; }
    public long growthCount() { return growthCount; }
    public long updateScratchCurrentBytes() { return updateScratchCurrentBytes; }
    public long updateScratchHighWaterBytes() { return updateScratchHighWaterBytes; }
    public long sidecarDirtyCount() { return sidecarDirtyCount; }
    public long sidecarRebuildCount() { return sidecarRebuildCount; }
    public long sidecarRebuildRows() { return sidecarRebuildRows; }
    public long sidecarScratchCurrentBytes() { return sidecarScratchCurrentBytes; }
    public long sidecarScratchHighWaterBytes() { return sidecarScratchHighWaterBytes; }
    public String lastOperation() { return lastOperation; }
    public OperationOutcome lastOutcome() { return lastOutcome; }
    public String lastErrorCode() { return lastErrorCode; }
    public long lastScanned() { return lastScanned; }
    public long lastMatched() { return lastMatched; }
    public long lastChanged() { return lastChanged; }
    public long childInstanceCount() { return childInstanceCount; }
    public long descendantRowCount() { return descendantRowCount; }
    public long materializationInvocationCount() { return materializationInvocationCount; }
    public long materializationFailureCount() { return materializationFailureCount; }
    public String lastMaterializationBudgetIdentity() { return lastMaterializationBudgetIdentity; }
    public int lastMaterializationMaximumOwnershipDepth() {
        return lastMaterializationMaximumOwnershipDepth;
    }
    public long lastMaterializationTableInstances() { return lastMaterializationTableInstances; }
    public long lastMaterializationRows() { return lastMaterializationRows; }
    public long lastMaterializationLeafValues() { return lastMaterializationLeafValues; }
    public long lastMaterializationEstimatedAllocationBytes() {
        return lastMaterializationEstimatedAllocationBytes;
    }
}
