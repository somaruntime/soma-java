package com.hgtech.soma.runtime.generated;

/**
 * Parent-owned per-physical-Table attribution over one GroupLedger。
 *
 * <p>It never owns a second budget. Every mutation is charged exactly once to the
 * parent GroupLedger while this object records the attributed amount.</p>
 */
final class TableLedger {
    private final GroupLedger group;
    private final int memberIndex;
    private final String table;
    private final ChildOwnershipRegistry ownership;
    private long retainedBytes;
    private long transientBytes;
    private boolean tableInstanceReserved;

    TableLedger(
            GroupLedger group,
            int memberIndex,
            String table,
            ChildOwnershipRegistry ownership) {
        this.group = group;
        this.memberIndex = memberIndex;
        this.table = table;
        this.ownership = ownership;
    }

    void reserveTableInstance(String operation) {
        if (tableInstanceReserved) {
            throw ownership.internalInvariant(
                    "table_ledger_instance_accounting", table, operation);
        }
        group.reserveTableInstance(
                memberIndex, table, operation, ownership);
        tableInstanceReserved = true;
    }

    void releaseTableInstance(String operation) {
        if (!tableInstanceReserved) {
            throw ownership.internalInvariant(
                    "table_ledger_instance_accounting", table, operation);
        }
        group.releaseTableInstance(
                memberIndex, table, operation, ownership);
        tableInstanceReserved = false;
    }

    void reserveRetained(long bytes, String operation) {
        group.reserveRetained(
                memberIndex, bytes, table, operation, ownership);
        retainedBytes = checkedAdd(retainedBytes, bytes, operation);
    }

    void releaseRetained(long bytes, String operation) {
        if (bytes < 0L || bytes > retainedBytes) {
            throw ownership.internalInvariant(
                    "table_ledger_retained_accounting", table, operation);
        }
        group.releaseRetained(
                memberIndex, bytes, table, operation, ownership);
        retainedBytes -= bytes;
    }

    void reserveTransient(long bytes, String operation) {
        group.reserveTransient(
                memberIndex, bytes, table, operation, ownership);
        transientBytes = checkedAdd(transientBytes, bytes, operation);
    }

    void releaseTransient(long bytes, String operation) {
        if (bytes < 0L || bytes > transientBytes) {
            throw ownership.internalInvariant(
                    "table_ledger_transient_accounting", table, operation);
        }
        group.releaseTransient(
                memberIndex, bytes, table, operation, ownership);
        transientBytes -= bytes;
    }

    void preflightRetained(
            long proposedRetained,
            long additionalTransient,
            String operation) {
        group.preflightRetainedReplacement(
                memberIndex,
                retainedBytes,
                proposedRetained,
                additionalTransient,
                table,
                operation,
                ownership);
    }

    private long checkedAdd(long left, long right, String operation) {
        if (right < 0L || Long.MAX_VALUE - left < right) {
            throw RuntimeFailures.memoryLimitExceeded(
                    table, operation, Long.MAX_VALUE, Long.MAX_VALUE);
        }
        return left + right;
    }
}
