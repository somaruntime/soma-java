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
            long lastChanged) {
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
                lastScanned, lastMatched, lastChanged);
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
}
