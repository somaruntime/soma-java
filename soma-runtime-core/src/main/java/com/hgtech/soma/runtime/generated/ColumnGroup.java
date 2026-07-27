package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.SomaRuntimeException;

/**
 * 以 stage-all-then-commit 方式协调同一 table 的 column capacity。
 */
public final class ColumnGroup {

    private final GeneratedColumn[] columns;
    private final String table;
    private final TablePlan tablePlan;
    private final TableLedger tableLedger;
    private final ChildOwnershipRegistry ownership;
    private int capacity;
    private long retainedBytes;
    private long externalRetainedBytes;
    private boolean released;

    public ColumnGroup(
            String table,
            TablePlan tablePlan,
            ChildOwnershipRegistry ownership,
            int initialCapacity,
            GeneratedColumn... columns) {
        GeneratedColumn.requireCapacity(initialCapacity);
        if (columns == null) {
            throw new NullPointerException("columns");
        }
        if (table == null || tablePlan == null || ownership == null) {
            throw new NullPointerException("column group binding");
        }
        this.table = table;
        this.tablePlan = tablePlan;
        this.ownership = ownership;
        this.tableLedger = ownership.newTableLedgerInternal(table);
        this.columns = columns.clone();
        validateColumns(this.columns);
        long initialBytes = estimatedBytes(initialCapacity);
        requireTableLimit(initialBytes, "table.create");
        long stagingBytes = stagingAllocationBytes(initialBytes);
        requireBulkLimit(stagingBytes, "table.create");
        tableLedger.reserveTableInstance("table.create");
        boolean bytesReserved = false;
        boolean transientReserved = false;
        try {
            tableLedger.reserveRetained(initialBytes, "table.create");
            bytesReserved = true;
            tableLedger.reserveTransient(
                    stagingCoordinatorBytes(), "table.create");
            transientReserved = true;
            stageAndCommit(initialCapacity);
            retainedBytes = initialBytes;
            this.capacity = initialCapacity;
        } catch (RuntimeException failure) {
            if (transientReserved) tableLedger.releaseTransient(
                    stagingCoordinatorBytes(), "table.create");
            if (bytesReserved) tableLedger.releaseRetained(
                    initialBytes, "table.create");
            tableLedger.releaseTableInstance("table.create");
            throw failure;
        } catch (Error failure) {
            if (transientReserved) tableLedger.releaseTransient(
                    stagingCoordinatorBytes(), "table.create");
            if (bytesReserved) tableLedger.releaseRetained(
                    initialBytes, "table.create");
            tableLedger.releaseTableInstance("table.create");
            throw failure;
        }
        tableLedger.releaseTransient(
                stagingCoordinatorBytes(), "table.create");
    }

    public int capacity() {
        return capacity;
    }

    ChildOwnershipRegistry ownershipInternal() {
        return ownership;
    }

    public boolean ensureCapacity(int required, int growthNumerator, int growthDenominator) {
        GeneratedColumn.requireCapacity(required);
        if (growthDenominator < 1 || growthNumerator <= growthDenominator) {
            throw new IllegalArgumentException("growth ratio must satisfy numerator > denominator >= 1");
        }
        if (required <= capacity) {
            return false;
        }

        int newCapacity = targetCapacity(required, growthNumerator, growthDenominator);
        long newBytes = estimatedBytes(newCapacity);
        requireTableLimit(newBytes, "capacity.grow");
        requireBulkLimit(stagingAllocationBytes(newBytes), "capacity.grow");
        long delta = newBytes - retainedBytes;
        tableLedger.reserveRetained(delta, "capacity.grow");
        long transientBytes = checkedAdd(retainedBytes, stagingCoordinatorBytes());
        boolean transientReserved = false;
        try {
            tableLedger.reserveTransient(
                    transientBytes, "capacity.grow");
            transientReserved = true;
            stageAndCommit(newCapacity);
        } catch (RuntimeException failure) {
            if (transientReserved) tableLedger.releaseTransient(
                    transientBytes, "capacity.grow");
            tableLedger.releaseRetained(delta, "capacity.grow");
            throw failure;
        } catch (Error failure) {
            if (transientReserved) tableLedger.releaseTransient(
                    transientBytes, "capacity.grow");
            tableLedger.releaseRetained(delta, "capacity.grow");
            throw failure;
        }
        tableLedger.releaseTransient(transientBytes, "capacity.grow");
        retainedBytes = newBytes;
        capacity = newCapacity;
        return true;
    }

    void preflightCapacity(
            int required,
            int growthNumerator,
            int growthDenominator,
            long proposedExternalRetainedBytes,
            String operation) {
        GeneratedColumn.requireCapacity(required);
        if (growthDenominator < 1 || growthNumerator <= growthDenominator) {
            throw new IllegalArgumentException(
                    "growth ratio must satisfy numerator > denominator >= 1");
        }
        if (proposedExternalRetainedBytes < 0L) {
            throw internalInvariant(
                    "table_storage_accounting", table, operation);
        }
        int proposedCapacity = required <= capacity
                ? capacity : targetCapacity(required, growthNumerator, growthDenominator);
        long proposedColumns = required <= capacity
                ? retainedBytes : estimatedBytes(proposedCapacity);
        long proposedTable = checkedAdd(proposedColumns, proposedExternalRetainedBytes);
        if (proposedTable > tablePlan.maximumTableStorageBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, tablePlan.maximumTableStorageBytes(), proposedTable);
        }
        if (required > capacity) {
            requireBulkLimit(stagingAllocationBytes(proposedColumns), operation);
        }

        long additionalTransient = required > capacity
                ? checkedAdd(retainedBytes, stagingCoordinatorBytes())
                : 0L;
        tableLedger.preflightRetained(
                proposedTable, additionalTransient, operation);
    }

    long retainedBytes() { return retainedBytes; }

    void replaceExternalRetainedBytes(
            long previous, long proposed, String operation) {
        if (previous != externalRetainedBytes || proposed < 0L) {
            throw internalInvariant(
                    "table_storage_accounting", table, operation);
        }
        preflightExternalRetainedBytes(proposed, operation);
        if (proposed > previous) {
            tableLedger.reserveRetained(proposed - previous, operation);
        } else if (previous > proposed) {
            tableLedger.releaseRetained(previous - proposed, operation);
        }
        externalRetainedBytes = proposed;
    }

    void preflightExternalRetainedBytes(long proposed, String operation) {
        if (proposed < 0L) {
            throw internalInvariant(
                    "table_storage_accounting", table, operation);
        }
        long tableBytes = checkedAdd(retainedBytes, proposed);
        if (tableBytes > tablePlan.maximumTableStorageBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, tablePlan.maximumTableStorageBytes(), tableBytes);
        }
        tableLedger.preflightRetained(tableBytes, 0L, operation);
    }

    void reserveTransientBytes(long bytes, String operation) {
        if (bytes < 0L) {
            throw internalInvariant(
                    "table_transient_storage_accounting", table, operation);
        }
        tableLedger.reserveTransient(bytes, operation);
    }

    void releaseTransientBytes(long bytes, String operation) {
        tableLedger.releaseTransient(bytes, operation);
    }

    void releaseStorage() {
        if (released) return;
        if (externalRetainedBytes != 0L) {
            throw internalInvariant(
                    "table_external_storage_release", table, "release");
        }
        for (int i = 0; i < columns.length; i++) columns[i].releaseStorage();
        tableLedger.releaseRetained(retainedBytes, "release");
        tableLedger.releaseTableInstance("release");
        retainedBytes = 0L;
        capacity = 0;
        released = true;
    }

    private static void validateColumns(GeneratedColumn[] columns) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i] == null) {
                throw new NullPointerException("columns[" + i + "]");
            }
            for (int j = 0; j < i; j++) {
                if (columns[i] == columns[j]) {
                    throw new IllegalArgumentException("duplicate column");
                }
            }
        }
    }

    private void stageAndCommit(int newCapacity) {
        Object[] staged = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            staged[i] = columns[i].stageCapacity(newCapacity);
        }
        for (int i = 0; i < columns.length; i++) {
            columns[i].commitCapacity(staged[i]);
        }
    }

    private int targetCapacity(
            int required, int growthNumerator, int growthDenominator) {
        long grown = ((long) capacity * (long) growthNumerator
                + (long) growthDenominator - 1L) / (long) growthDenominator;
        long target = Math.max((long) required, grown);
        if (target > tablePlan.maximumRows()) {
            target = tablePlan.maximumRows();
        }
        return (int) target;
    }

    private long estimatedBytes(int newCapacity) {
        long total = 0L;
        for (int i = 0; i < columns.length; i++) {
            long bytes = columns[i].estimatedBytes(newCapacity);
            if (bytes < 0L || Long.MAX_VALUE - total < bytes) return Long.MAX_VALUE;
            total += bytes;
        }
        return total;
    }

    private long stagingCoordinatorBytes() {
        return 8L * (long) columns.length;
    }

    private long stagingAllocationBytes(long stagedColumnBytes) {
        return checkedAdd(stagedColumnBytes, stagingCoordinatorBytes());
    }

    private void requireTableLimit(long proposedColumns, String operation) {
        long proposed = checkedAdd(proposedColumns, externalRetainedBytes);
        if (proposed > tablePlan.maximumTableStorageBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, tablePlan.maximumTableStorageBytes(), proposed);
        }
    }

    private static long checkedAdd(long left, long right) {
        return left < 0L || right < 0L || Long.MAX_VALUE - left < right
                ? Long.MAX_VALUE : left + right;
    }

    private void requireBulkLimit(long proposed, String operation) {
        if (proposed > tablePlan.maximumBulkScratchBytes()) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, tablePlan.maximumBulkScratchBytes(), proposed);
        }
    }

    private SomaRuntimeException internalInvariant(
            String invariant, String path, String operation) {
        return ownership.internalInvariant(invariant, path, operation);
    }
}
