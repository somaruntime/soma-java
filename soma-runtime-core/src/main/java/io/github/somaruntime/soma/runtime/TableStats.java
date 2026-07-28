package io.github.somaruntime.soma.runtime;

/** Immutable self-consistent table stats snapshot。 */
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

    private final long operationScratchCurrentBytes;
    private final long operationScratchHighWaterBytes;
    private final String keySpaceImplementation;
    private final int keySpaceCapacity;
    private final int keySpaceUsed;
    private final long keySpaceProbeCount;
    private final long keySpaceCollisionCount;
    private final long keySpaceRehashCount;
    private final long keySpaceStorageCurrentBytes;
    private final long keySpaceStorageHighWaterBytes;

    private final int exactIndexCount;
    private final long exactIndexEntryCount;
    private final long exactIndexGroupCount;
    private final long exactIndexProbeCount;
    private final long exactIndexCollisionCount;
    private final long exactIndexRehashCount;
    private final long exactIndexStorageCurrentBytes;
    private final long exactIndexStorageHighWaterBytes;

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
            long lastMaterializationEstimatedAllocationBytes,
            long operationScratchCurrentBytes,
            long operationScratchHighWaterBytes,
            String keySpaceImplementation,
            int keySpaceCapacity,
            int keySpaceUsed,
            long keySpaceProbeCount,
            long keySpaceCollisionCount,
            long keySpaceRehashCount,
            long keySpaceStorageCurrentBytes,
            long keySpaceStorageHighWaterBytes,
            int exactIndexCount,
            long exactIndexEntryCount,
            long exactIndexGroupCount,
            long exactIndexProbeCount,
            long exactIndexCollisionCount,
            long exactIndexRehashCount,
            long exactIndexStorageCurrentBytes,
            long exactIndexStorageHighWaterBytes) {
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
        this.lastMaterializationEstimatedAllocationBytes =
                lastMaterializationEstimatedAllocationBytes;
        this.operationScratchCurrentBytes = operationScratchCurrentBytes;
        this.operationScratchHighWaterBytes = operationScratchHighWaterBytes;
        this.keySpaceImplementation = keySpaceImplementation;
        this.keySpaceCapacity = keySpaceCapacity;
        this.keySpaceUsed = keySpaceUsed;
        this.keySpaceProbeCount = keySpaceProbeCount;
        this.keySpaceCollisionCount = keySpaceCollisionCount;
        this.keySpaceRehashCount = keySpaceRehashCount;
        this.keySpaceStorageCurrentBytes = keySpaceStorageCurrentBytes;
        this.keySpaceStorageHighWaterBytes = keySpaceStorageHighWaterBytes;
        this.exactIndexCount = exactIndexCount;
        this.exactIndexEntryCount = exactIndexEntryCount;
        this.exactIndexGroupCount = exactIndexGroupCount;
        this.exactIndexProbeCount = exactIndexProbeCount;
        this.exactIndexCollisionCount = exactIndexCollisionCount;
        this.exactIndexRehashCount = exactIndexRehashCount;
        this.exactIndexStorageCurrentBytes = exactIndexStorageCurrentBytes;
        this.exactIndexStorageHighWaterBytes = exactIndexStorageHighWaterBytes;
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
                || lastScanned < 0L || lastMatched < 0L || lastChanged < 0L
                || lastChanged > lastMatched || lastMatched > lastScanned) {
            throw new IllegalArgumentException("invalid table stats snapshot");
        }
        return new TableStats(schemaHash, runtimeCompatibility, runtimePlanHash,
                statsMode, rows, capacity, structuralEpoch, released, activeViews,
                growthCount, updateScratchCurrentBytes, updateScratchHighWaterBytes,
                lastOperation, lastOutcome, lastErrorCode,
                lastScanned, lastMatched, lastChanged,
                0L, 0L, 0L, 0L, "", 0, 0L, 0L, 0L, 0L,
                0L, 0L, "", 0, 0, 0L, 0L, 0L, 0L, 0L,
                0, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public static TableStats withOwnershipAndMaterialization(
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
        requireBase(base);
        if (lastMaterializationBudgetIdentity == null) {
            throw new NullPointerException("lastMaterializationBudgetIdentity");
        }
        if (childInstanceCount < 0L || descendantRowCount < 0L
                || materializationInvocationCount < 0L
                || materializationFailureCount < 0L
                || materializationFailureCount > materializationInvocationCount
                || lastMaterializationMaximumOwnershipDepth < 0
                || lastMaterializationTableInstances < 0L
                || lastMaterializationRows < 0L
                || lastMaterializationLeafValues < 0L
                || lastMaterializationEstimatedAllocationBytes < 0L) {
            throw new IllegalArgumentException("invalid ownership/materialization stats");
        }
        return copy(base,
                childInstanceCount, descendantRowCount,
                materializationInvocationCount, materializationFailureCount,
                lastMaterializationBudgetIdentity,
                lastMaterializationMaximumOwnershipDepth,
                lastMaterializationTableInstances, lastMaterializationRows,
                lastMaterializationLeafValues,
                lastMaterializationEstimatedAllocationBytes,
                base.operationScratchCurrentBytes, base.operationScratchHighWaterBytes,
                base.keySpaceImplementation, base.keySpaceCapacity, base.keySpaceUsed,
                base.keySpaceProbeCount, base.keySpaceCollisionCount,
                base.keySpaceRehashCount,
                base.keySpaceStorageCurrentBytes,
                base.keySpaceStorageHighWaterBytes,
                base.exactIndexCount, base.exactIndexEntryCount,
                base.exactIndexGroupCount, base.exactIndexProbeCount,
                base.exactIndexCollisionCount, base.exactIndexRehashCount,
                base.exactIndexStorageCurrentBytes,
                base.exactIndexStorageHighWaterBytes);
    }

    public static TableStats withOperationScratch(
            TableStats base,
            long operationScratchCurrentBytes,
            long operationScratchHighWaterBytes) {
        requireBase(base);
        if (operationScratchCurrentBytes < 0L
                || operationScratchHighWaterBytes < operationScratchCurrentBytes) {
            throw new IllegalArgumentException("invalid operation scratch stats");
        }
        return copy(base,
                base.childInstanceCount, base.descendantRowCount,
                base.materializationInvocationCount, base.materializationFailureCount,
                base.lastMaterializationBudgetIdentity,
                base.lastMaterializationMaximumOwnershipDepth,
                base.lastMaterializationTableInstances, base.lastMaterializationRows,
                base.lastMaterializationLeafValues,
                base.lastMaterializationEstimatedAllocationBytes,
                operationScratchCurrentBytes, operationScratchHighWaterBytes,
                base.keySpaceImplementation, base.keySpaceCapacity, base.keySpaceUsed,
                base.keySpaceProbeCount, base.keySpaceCollisionCount,
                base.keySpaceRehashCount,
                base.keySpaceStorageCurrentBytes,
                base.keySpaceStorageHighWaterBytes,
                base.exactIndexCount, base.exactIndexEntryCount,
                base.exactIndexGroupCount, base.exactIndexProbeCount,
                base.exactIndexCollisionCount, base.exactIndexRehashCount,
                base.exactIndexStorageCurrentBytes,
                base.exactIndexStorageHighWaterBytes);
    }

    public static TableStats withKeySpace(
            TableStats base,
            String keySpaceImplementation,
            int keySpaceCapacity,
            int keySpaceUsed,
            long keySpaceProbeCount,
            long keySpaceCollisionCount,
            long keySpaceRehashCount,
            long keySpaceStorageCurrentBytes,
            long keySpaceStorageHighWaterBytes) {
        requireBase(base);
        if (keySpaceImplementation == null) {
            throw new NullPointerException("keySpaceImplementation");
        }
        boolean absent = keySpaceImplementation.isEmpty();
        if (keySpaceCapacity < 0 || keySpaceUsed < 0 || keySpaceUsed > keySpaceCapacity
                || keySpaceProbeCount < 0L || keySpaceCollisionCount < 0L
                || keySpaceCollisionCount > keySpaceProbeCount
                || keySpaceRehashCount < 0L
                || keySpaceStorageCurrentBytes < 0L
                || keySpaceStorageHighWaterBytes < keySpaceStorageCurrentBytes
                || (absent && (keySpaceCapacity != 0 || keySpaceUsed != 0
                || keySpaceProbeCount != 0L || keySpaceCollisionCount != 0L
                || keySpaceRehashCount != 0L
                || keySpaceStorageCurrentBytes != 0L
                || keySpaceStorageHighWaterBytes != 0L))) {
            throw new IllegalArgumentException("invalid key-space stats");
        }
        return copy(base,
                base.childInstanceCount, base.descendantRowCount,
                base.materializationInvocationCount, base.materializationFailureCount,
                base.lastMaterializationBudgetIdentity,
                base.lastMaterializationMaximumOwnershipDepth,
                base.lastMaterializationTableInstances, base.lastMaterializationRows,
                base.lastMaterializationLeafValues,
                base.lastMaterializationEstimatedAllocationBytes,
                base.operationScratchCurrentBytes, base.operationScratchHighWaterBytes,
                keySpaceImplementation, keySpaceCapacity, keySpaceUsed,
                keySpaceProbeCount, keySpaceCollisionCount, keySpaceRehashCount,
                keySpaceStorageCurrentBytes, keySpaceStorageHighWaterBytes,
                base.exactIndexCount, base.exactIndexEntryCount,
                base.exactIndexGroupCount, base.exactIndexProbeCount,
                base.exactIndexCollisionCount, base.exactIndexRehashCount,
                base.exactIndexStorageCurrentBytes,
                base.exactIndexStorageHighWaterBytes);
    }

    public static TableStats withExactIndexes(
            TableStats base,
            int exactIndexCount,
            long exactIndexEntryCount,
            long exactIndexGroupCount,
            long exactIndexProbeCount,
            long exactIndexCollisionCount,
            long exactIndexRehashCount,
            long exactIndexStorageCurrentBytes,
            long exactIndexStorageHighWaterBytes) {
        requireBase(base);
        boolean absent = exactIndexCount == 0;
        if (exactIndexCount < 0 || exactIndexEntryCount < 0L
                || exactIndexGroupCount < 0L
                || exactIndexGroupCount > exactIndexEntryCount
                || exactIndexProbeCount < 0L || exactIndexCollisionCount < 0L
                || exactIndexCollisionCount > exactIndexProbeCount
                || exactIndexRehashCount < 0L
                || exactIndexStorageCurrentBytes < 0L
                || exactIndexStorageHighWaterBytes < exactIndexStorageCurrentBytes
                || (absent && (exactIndexEntryCount != 0L
                || exactIndexGroupCount != 0L || exactIndexProbeCount != 0L
                || exactIndexCollisionCount != 0L || exactIndexRehashCount != 0L
                || exactIndexStorageCurrentBytes != 0L
                || exactIndexStorageHighWaterBytes != 0L))) {
            throw new IllegalArgumentException("invalid exact-index stats");
        }
        return copy(base,
                base.childInstanceCount, base.descendantRowCount,
                base.materializationInvocationCount, base.materializationFailureCount,
                base.lastMaterializationBudgetIdentity,
                base.lastMaterializationMaximumOwnershipDepth,
                base.lastMaterializationTableInstances, base.lastMaterializationRows,
                base.lastMaterializationLeafValues,
                base.lastMaterializationEstimatedAllocationBytes,
                base.operationScratchCurrentBytes, base.operationScratchHighWaterBytes,
                base.keySpaceImplementation, base.keySpaceCapacity, base.keySpaceUsed,
                base.keySpaceProbeCount, base.keySpaceCollisionCount,
                base.keySpaceRehashCount,
                base.keySpaceStorageCurrentBytes,
                base.keySpaceStorageHighWaterBytes,
                exactIndexCount, exactIndexEntryCount, exactIndexGroupCount,
                exactIndexProbeCount, exactIndexCollisionCount, exactIndexRehashCount,
                exactIndexStorageCurrentBytes, exactIndexStorageHighWaterBytes);
    }

    private static TableStats copy(
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
            long lastMaterializationEstimatedAllocationBytes,
            long operationScratchCurrentBytes,
            long operationScratchHighWaterBytes,
            String keySpaceImplementation,
            int keySpaceCapacity,
            int keySpaceUsed,
            long keySpaceProbeCount,
            long keySpaceCollisionCount,
            long keySpaceRehashCount,
            long keySpaceStorageCurrentBytes,
            long keySpaceStorageHighWaterBytes,
            int exactIndexCount,
            long exactIndexEntryCount,
            long exactIndexGroupCount,
            long exactIndexProbeCount,
            long exactIndexCollisionCount,
            long exactIndexRehashCount,
            long exactIndexStorageCurrentBytes,
            long exactIndexStorageHighWaterBytes) {
        return new TableStats(base.schemaHash, base.runtimeCompatibility,
                base.runtimePlanHash, base.statsMode, base.rows, base.capacity,
                base.structuralEpoch, base.released, base.activeViews,
                base.growthCount, base.updateScratchCurrentBytes,
                base.updateScratchHighWaterBytes,
                base.lastOperation, base.lastOutcome, base.lastErrorCode,
                base.lastScanned, base.lastMatched, base.lastChanged,
                childInstanceCount, descendantRowCount,
                materializationInvocationCount, materializationFailureCount,
                lastMaterializationBudgetIdentity,
                lastMaterializationMaximumOwnershipDepth,
                lastMaterializationTableInstances, lastMaterializationRows,
                lastMaterializationLeafValues,
                lastMaterializationEstimatedAllocationBytes,
                operationScratchCurrentBytes, operationScratchHighWaterBytes,
                keySpaceImplementation, keySpaceCapacity, keySpaceUsed,
                keySpaceProbeCount, keySpaceCollisionCount, keySpaceRehashCount,
                keySpaceStorageCurrentBytes, keySpaceStorageHighWaterBytes,
                exactIndexCount, exactIndexEntryCount, exactIndexGroupCount,
                exactIndexProbeCount, exactIndexCollisionCount, exactIndexRehashCount,
                exactIndexStorageCurrentBytes, exactIndexStorageHighWaterBytes);
    }

    private static void requireBase(TableStats base) {
        if (base == null) throw new NullPointerException("base");
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
    public String lastMaterializationBudgetIdentity() {
        return lastMaterializationBudgetIdentity;
    }
    public int lastMaterializationMaximumOwnershipDepth() {
        return lastMaterializationMaximumOwnershipDepth;
    }
    public long lastMaterializationTableInstances() {
        return lastMaterializationTableInstances;
    }
    public long lastMaterializationRows() { return lastMaterializationRows; }
    public long lastMaterializationLeafValues() { return lastMaterializationLeafValues; }
    public long lastMaterializationEstimatedAllocationBytes() {
        return lastMaterializationEstimatedAllocationBytes;
    }
    public long operationScratchCurrentBytes() { return operationScratchCurrentBytes; }
    public long operationScratchHighWaterBytes() { return operationScratchHighWaterBytes; }
    public String keySpaceImplementation() { return keySpaceImplementation; }
    public int keySpaceCapacity() { return keySpaceCapacity; }
    public int keySpaceUsed() { return keySpaceUsed; }
    public long keySpaceProbeCount() { return keySpaceProbeCount; }
    public long keySpaceCollisionCount() { return keySpaceCollisionCount; }
    public long keySpaceRehashCount() { return keySpaceRehashCount; }
    public long keySpaceStorageCurrentBytes() {
        return keySpaceStorageCurrentBytes;
    }
    public long keySpaceStorageHighWaterBytes() {
        return keySpaceStorageHighWaterBytes;
    }
    public int exactIndexCount() { return exactIndexCount; }
    public long exactIndexEntryCount() { return exactIndexEntryCount; }
    public long exactIndexGroupCount() { return exactIndexGroupCount; }
    public long exactIndexProbeCount() { return exactIndexProbeCount; }
    public long exactIndexCollisionCount() { return exactIndexCollisionCount; }
    public long exactIndexRehashCount() { return exactIndexRehashCount; }
    public long exactIndexStorageCurrentBytes() {
        return exactIndexStorageCurrentBytes;
    }
    public long exactIndexStorageHighWaterBytes() {
        return exactIndexStorageHighWaterBytes;
    }
}
