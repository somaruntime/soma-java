package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.SomaRuntimeException;

/** Ownership aggregate共享的deterministic current-storage与table-instance admission。 */
final class StorageBudget {
    private final long maximumBytes;
    private final long maximumTableInstances;
    private final ChildOwnershipRegistry ownership;
    private long currentBytes;
    private long transientBytes;
    private long highWaterBytes;
    private long currentTableInstances;
    private long highWaterTableInstances;

    public StorageBudget(
            long maximumBytes,
            long maximumTableInstances,
            ChildOwnershipRegistry ownership) {
        if (maximumBytes <= 0L || maximumTableInstances <= 0L) {
            throw new IllegalArgumentException("aggregate storage limits must be positive");
        }
        this.maximumBytes = maximumBytes;
        this.maximumTableInstances = maximumTableInstances;
        this.ownership = ownership;
    }

    public void reserveTableInstance(String table, String operation) {
        long proposed = checkedAdd(currentTableInstances, 1L, table, operation);
        if (proposed > maximumTableInstances) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, maximumTableInstances, proposed);
        }
        currentTableInstances = proposed;
        if (proposed > highWaterTableInstances) highWaterTableInstances = proposed;
    }

    public void releaseTableInstance() {
        if (currentTableInstances <= 0L) {
            throw internalInvariant(
                    "aggregate_table_instance_accounting", "ownership", "release");
        }
        currentTableInstances--;
    }

    public void reserveBytes(long bytes, String table, String operation) {
        if (bytes < 0L) {
            throw internalInvariant(
                    "aggregate_storage_accounting", table, operation);
        }
        long retained = checkedAdd(currentBytes, bytes, table, operation);
        long proposed = checkedAdd(retained, transientBytes, table, operation);
        if (proposed > maximumBytes) {
            throw RuntimeFailures.memoryLimitExceeded(table, operation, maximumBytes, proposed);
        }
        currentBytes = retained;
        if (proposed > highWaterBytes) highWaterBytes = proposed;
    }

    public void releaseBytes(long bytes, String table, String operation) {
        if (bytes < 0L || bytes > currentBytes) {
            throw internalInvariant(
                    "aggregate_storage_accounting", table, operation);
        }
        currentBytes -= bytes;
    }

    /**
     * Reserves short-lived runtime-owned staging storage without turning it into retained
     * table state. The single-owner runtime must release the same amount in a finally path.
     */
    public void reserveTransientBytes(long bytes, String table, String operation) {
        if (bytes < 0L) {
            throw internalInvariant(
                    "aggregate_transient_storage_accounting", table, operation);
        }
        long proposedTransient = checkedAdd(transientBytes, bytes, table, operation);
        long proposed = checkedAdd(currentBytes, proposedTransient, table, operation);
        if (proposed > maximumBytes) {
            throw RuntimeFailures.memoryLimitExceeded(table, operation, maximumBytes, proposed);
        }
        transientBytes = proposedTransient;
        if (proposed > highWaterBytes) highWaterBytes = proposed;
    }

    public void releaseTransientBytes(long bytes, String table, String operation) {
        if (bytes < 0L || bytes > transientBytes) {
            throw internalInvariant(
                    "aggregate_transient_storage_accounting", table, operation);
        }
        transientBytes -= bytes;
    }

    public long maximumBytes() { return maximumBytes; }
    public long maximumTableInstances() { return maximumTableInstances; }
    public long currentBytes() { return currentBytes; }
    public long transientBytes() { return transientBytes; }
    public long totalCurrentBytes() {
        return checkedAdd(currentBytes, transientBytes, "ownership", "statsSnapshot");
    }
    public long highWaterBytes() { return highWaterBytes; }
    public long currentTableInstances() { return currentTableInstances; }
    public long highWaterTableInstances() { return highWaterTableInstances; }

    private static long checkedAdd(
            long current, long delta, String table, String operation) {
        if (delta < 0L || Long.MAX_VALUE - current < delta) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, Long.MAX_VALUE, Long.MAX_VALUE);
        }
        return current + delta;
    }

    private SomaRuntimeException internalInvariant(
            String invariant, String path, String operation) {
        return ownership.internalInvariant(invariant, path, operation);
    }
}
