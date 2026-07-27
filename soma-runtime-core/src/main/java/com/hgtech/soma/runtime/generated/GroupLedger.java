package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.SomaRuntimeException;

/**
 * Sole hard owner of SomaGroup member entitlement, retained structural bytes and
 * storage/access transient bytes。
 *
 * <p>This generated-runtime protocol type is single-owner and deliberately not
 * synchronized. Invocation scratch/output are owned by a separate DataFlow ledger.</p>
 */
public final class GroupLedger {
    private final String groupPath;
    private final long maximumBytes;
    private final long maximumTableInstances;
    private final String[] memberIds;
    private final long[] memberMaximumBytes;
    private final long[] memberMaximumTableInstances;
    private final long[] memberRetainedBytes;
    private final long[] memberTransientBytes;
    private final long[] memberHighWaterBytes;
    private final long[] memberCurrentTableInstances;
    private final long[] memberHighWaterTableInstances;

    private long retainedBytes;
    private long transientBytes;
    private long highWaterBytes;
    private long currentTableInstances;
    private long highWaterTableInstances;

    public GroupLedger(
            String groupPath,
            long maximumBytes,
            long maximumTableInstances,
            String[] memberIds,
            long[] memberMaximumBytes,
            long[] memberMaximumTableInstances) {
        if (groupPath == null) throw new NullPointerException("groupPath");
        if (maximumBytes <= 0L || maximumTableInstances <= 0L) {
            throw new IllegalArgumentException("Group ledger limits must be positive");
        }
        if (memberIds == null || memberMaximumBytes == null
                || memberMaximumTableInstances == null) {
            throw new NullPointerException("member entitlements");
        }
        if (memberIds.length == 0
                || memberIds.length != memberMaximumBytes.length
                || memberIds.length != memberMaximumTableInstances.length) {
            throw new IllegalArgumentException(
                    "Group ledger member entitlement shape mismatch");
        }
        long entitlementBytes = 0L;
        long entitlementInstances = 0L;
        for (int index = 0; index < memberIds.length; index++) {
            if (memberIds[index] == null || memberIds[index].isEmpty()
                    || memberMaximumBytes[index] <= 0L
                    || memberMaximumTableInstances[index] <= 0L) {
                throw new IllegalArgumentException(
                        "invalid Group ledger member entitlement");
            }
            entitlementBytes = checkedEntitlementSum(
                    entitlementBytes, memberMaximumBytes[index]);
            entitlementInstances = checkedEntitlementSum(
                    entitlementInstances, memberMaximumTableInstances[index]);
        }
        if (entitlementBytes > maximumBytes
                || entitlementInstances > maximumTableInstances) {
            throw new IllegalArgumentException(
                    "Group envelope is smaller than member entitlements");
        }
        this.groupPath = groupPath;
        this.maximumBytes = maximumBytes;
        this.maximumTableInstances = maximumTableInstances;
        this.memberIds = memberIds.clone();
        this.memberMaximumBytes = memberMaximumBytes.clone();
        this.memberMaximumTableInstances =
                memberMaximumTableInstances.clone();
        memberRetainedBytes = new long[memberIds.length];
        memberTransientBytes = new long[memberIds.length];
        memberHighWaterBytes = new long[memberIds.length];
        memberCurrentTableInstances = new long[memberIds.length];
        memberHighWaterTableInstances = new long[memberIds.length];
    }

    public static GroupLedger standalone(
            long maximumBytes, long maximumTableInstances) {
        return new GroupLedger(
                "standalone",
                maximumBytes,
                maximumTableInstances,
                new String[] {"root"},
                new long[] {maximumBytes},
                new long[] {maximumTableInstances});
    }

    TableLedger newTableLedger(
            int memberIndex,
            String table,
            ChildOwnershipRegistry ownership) {
        requireMember(memberIndex, ownership, table, "table.create");
        return new TableLedger(this, memberIndex, table, ownership);
    }

    void reserveTableInstance(
            int memberIndex,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireMember(memberIndex, ownership, path, operation);
        long memberProposed = checkedAdd(
                memberCurrentTableInstances[memberIndex], 1L,
                path, operation);
        long groupProposed = checkedAdd(
                currentTableInstances, 1L, path, operation);
        if (memberProposed > memberMaximumTableInstances[memberIndex]) {
            throw RuntimeFailures.memoryLimitExceeded(
                    path, operation,
                    memberMaximumTableInstances[memberIndex], memberProposed);
        }
        if (groupProposed > maximumTableInstances) {
            throw RuntimeFailures.memoryLimitExceeded(
                    groupPath, operation,
                    maximumTableInstances, groupProposed);
        }
        memberCurrentTableInstances[memberIndex] = memberProposed;
        currentTableInstances = groupProposed;
        if (memberProposed > memberHighWaterTableInstances[memberIndex]) {
            memberHighWaterTableInstances[memberIndex] = memberProposed;
        }
        if (groupProposed > highWaterTableInstances) {
            highWaterTableInstances = groupProposed;
        }
    }

    void releaseTableInstance(
            int memberIndex,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireMember(memberIndex, ownership, path, operation);
        if (memberCurrentTableInstances[memberIndex] <= 0L
                || currentTableInstances <= 0L) {
            throw internal(
                    ownership, "group_table_instance_accounting",
                    path, operation);
        }
        memberCurrentTableInstances[memberIndex]--;
        currentTableInstances--;
    }

    void reserveRetained(
            int memberIndex,
            long bytes,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireNonNegative(bytes, ownership,
                "group_retained_accounting", path, operation);
        long memberRetained = checkedAdd(
                memberRetainedBytes[memberIndex], bytes, path, operation);
        long groupRetained = checkedAdd(retainedBytes, bytes, path, operation);
        requireByteLimits(
                memberIndex,
                memberRetained,
                memberTransientBytes[memberIndex],
                groupRetained,
                transientBytes,
                path,
                operation);
        memberRetainedBytes[memberIndex] = memberRetained;
        retainedBytes = groupRetained;
        updateByteHighWater(memberIndex);
    }

    void releaseRetained(
            int memberIndex,
            long bytes,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireNonNegative(bytes, ownership,
                "group_retained_accounting", path, operation);
        if (bytes > memberRetainedBytes[memberIndex] || bytes > retainedBytes) {
            throw internal(
                    ownership, "group_retained_accounting",
                    path, operation);
        }
        memberRetainedBytes[memberIndex] -= bytes;
        retainedBytes -= bytes;
    }

    void reserveTransient(
            int memberIndex,
            long bytes,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireNonNegative(bytes, ownership,
                "group_transient_accounting", path, operation);
        long memberTransient = checkedAdd(
                memberTransientBytes[memberIndex], bytes, path, operation);
        long groupTransient = checkedAdd(
                transientBytes, bytes, path, operation);
        requireByteLimits(
                memberIndex,
                memberRetainedBytes[memberIndex],
                memberTransient,
                retainedBytes,
                groupTransient,
                path,
                operation);
        memberTransientBytes[memberIndex] = memberTransient;
        transientBytes = groupTransient;
        updateByteHighWater(memberIndex);
    }

    void releaseTransient(
            int memberIndex,
            long bytes,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireNonNegative(bytes, ownership,
                "group_transient_accounting", path, operation);
        if (bytes > memberTransientBytes[memberIndex]
                || bytes > transientBytes) {
            throw internal(
                    ownership, "group_transient_accounting",
                    path, operation);
        }
        memberTransientBytes[memberIndex] -= bytes;
        transientBytes -= bytes;
    }

    void preflightRetainedReplacement(
            int memberIndex,
            long currentTableRetained,
            long proposedTableRetained,
            long additionalTransient,
            String path,
            String operation,
            ChildOwnershipRegistry ownership) {
        requireNonNegative(currentTableRetained, ownership,
                "table_ledger_accounting", path, operation);
        requireNonNegative(proposedTableRetained, ownership,
                "table_ledger_accounting", path, operation);
        requireNonNegative(additionalTransient, ownership,
                "table_ledger_accounting", path, operation);
        if (currentTableRetained > memberRetainedBytes[memberIndex]
                || currentTableRetained > retainedBytes) {
            throw internal(
                    ownership, "table_ledger_accounting", path, operation);
        }
        long memberProposedRetained = checkedAdd(
                memberRetainedBytes[memberIndex] - currentTableRetained,
                proposedTableRetained, path, operation);
        long groupProposedRetained = checkedAdd(
                retainedBytes - currentTableRetained,
                proposedTableRetained, path, operation);
        long memberProposedTransient = checkedAdd(
                memberTransientBytes[memberIndex],
                additionalTransient, path, operation);
        long groupProposedTransient = checkedAdd(
                transientBytes, additionalTransient, path, operation);
        requireByteLimits(
                memberIndex,
                memberProposedRetained,
                memberProposedTransient,
                groupProposedRetained,
                groupProposedTransient,
                path,
                operation);
    }

    public void requireMemberDrained(
            int memberIndex,
            ChildOwnershipRegistry ownership,
            String operation) {
        requireMember(memberIndex, ownership, groupPath, operation);
        if (memberRetainedBytes[memberIndex] != 0L
                || memberTransientBytes[memberIndex] != 0L
                || memberCurrentTableInstances[memberIndex] != 0L) {
            throw internal(
                    ownership, "group_member_ledger_drain",
                    memberIds[memberIndex], operation);
        }
    }

    public void requireDrained(String operation) {
        if (retainedBytes != 0L || transientBytes != 0L
                || currentTableInstances != 0L) {
            throw RuntimeFailures.internalInvariant(
                    "group_ledger_drain", groupPath, operation);
        }
    }

    public long maximumBytes() { return maximumBytes; }
    public long maximumTableInstances() { return maximumTableInstances; }
    public long retainedBytes() { return retainedBytes; }
    public long transientBytes() { return transientBytes; }
    public long currentBytes() {
        return retainedBytes > Long.MAX_VALUE - transientBytes
                ? Long.MAX_VALUE : retainedBytes + transientBytes;
    }
    public long highWaterBytes() { return highWaterBytes; }
    public long currentTableInstances() { return currentTableInstances; }
    public long highWaterTableInstances() { return highWaterTableInstances; }
    public long memberRetainedBytes(int memberIndex) {
        requireMemberIndex(memberIndex);
        return memberRetainedBytes[memberIndex];
    }
    public long memberTransientBytes(int memberIndex) {
        requireMemberIndex(memberIndex);
        return memberTransientBytes[memberIndex];
    }
    public long memberCurrentTableInstances(int memberIndex) {
        requireMemberIndex(memberIndex);
        return memberCurrentTableInstances[memberIndex];
    }

    private void requireByteLimits(
            int memberIndex,
            long proposedMemberRetained,
            long proposedMemberTransient,
            long proposedGroupRetained,
            long proposedGroupTransient,
            String path,
            String operation) {
        long proposedMember = checkedAdd(
                proposedMemberRetained, proposedMemberTransient,
                path, operation);
        long proposedGroup = checkedAdd(
                proposedGroupRetained, proposedGroupTransient,
                path, operation);
        if (proposedMember > memberMaximumBytes[memberIndex]) {
            throw RuntimeFailures.memoryLimitExceeded(
                    path, operation,
                    memberMaximumBytes[memberIndex], proposedMember);
        }
        if (proposedGroup > maximumBytes) {
            throw RuntimeFailures.memoryLimitExceeded(
                    groupPath, operation, maximumBytes, proposedGroup);
        }
    }

    private void updateByteHighWater(int memberIndex) {
        long memberCurrent = memberRetainedBytes[memberIndex]
                + memberTransientBytes[memberIndex];
        long groupCurrent = retainedBytes + transientBytes;
        if (memberCurrent > memberHighWaterBytes[memberIndex]) {
            memberHighWaterBytes[memberIndex] = memberCurrent;
        }
        if (groupCurrent > highWaterBytes) highWaterBytes = groupCurrent;
    }

    private void requireMember(
            int memberIndex,
            ChildOwnershipRegistry ownership,
            String path,
            String operation) {
        if (memberIndex < 0 || memberIndex >= memberIds.length) {
            throw internal(
                    ownership, "group_member_ledger_identity",
                    path, operation);
        }
    }

    private void requireMemberIndex(int memberIndex) {
        if (memberIndex < 0 || memberIndex >= memberIds.length) {
            throw new IndexOutOfBoundsException("memberIndex");
        }
    }

    private static void requireNonNegative(
            long value,
            ChildOwnershipRegistry ownership,
            String invariant,
            String path,
            String operation) {
        if (value < 0L) {
            throw internal(ownership, invariant, path, operation);
        }
    }

    private static long checkedAdd(
            long left, long right, String path, String operation) {
        if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) {
            throw RuntimeFailures.memoryLimitExceeded(
                    path, operation, Long.MAX_VALUE, Long.MAX_VALUE);
        }
        return left + right;
    }

    private static long checkedEntitlementSum(long left, long right) {
        if (right <= 0L || Long.MAX_VALUE - left < right) {
            throw new IllegalArgumentException(
                    "Group member entitlement sum overflow");
        }
        return left + right;
    }

    private static SomaRuntimeException internal(
            ChildOwnershipRegistry ownership,
            String invariant,
            String path,
            String operation) {
        return ownership == null
                ? RuntimeFailures.internalInvariant(invariant, path, operation)
                : ownership.internalInvariant(invariant, path, operation);
    }
}
