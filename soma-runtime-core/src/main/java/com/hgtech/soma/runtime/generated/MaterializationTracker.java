package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.MaterializationBudget;

import java.util.Arrays;
import java.util.Objects;

/** 单次 materialization invocation 的 overflow-safe deterministic budget tracker。 */
public final class MaterializationTracker {
    private final MaterializationBudget budget;
    private final String rootPath;
    private long tableInstances;
    private long rows;
    private long leafValues;
    private long estimatedBytes;
    private int maximumDepth;
    private Object[] ownershipStack = new Object[8];
    private int ownershipStackSize;

    public MaterializationTracker(MaterializationBudget budget, String rootPath) {
        this.budget = Objects.requireNonNull(budget, "budget");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    public void checkOwnershipDepth(int depth) {
        checkOwnershipDepth(depth, rootPath);
    }

    public void checkOwnershipDepth(int depth, String path) {
        if (depth < 0) {
            throw RuntimeFailures.internalInvariant(
                    "negative_materialization_depth", path, "materialize");
        }
        if (depth > maximumDepth) maximumDepth = depth;
        if (depth > budget.maximumOwnershipDepth()) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    "maximumOwnershipDepth",
                    budget.maximumOwnershipDepth(),
                    depth == 0 ? 0L : depth - 1L,
                    depth,
                    budget.identity(),
                    path);
        }
    }

    public void addTableInstances(long count) {
        addTableInstances(count, rootPath);
    }

    public void addTableInstances(long count, String path) {
        tableInstances = add("maximumTableInstances", tableInstances, count,
                budget.maximumTableInstances(), path);
    }

    public void addRows(long count) {
        addRows(count, rootPath);
    }

    public void addRows(long count, String path) {
        rows = add("maximumRows", rows, count, budget.maximumRows(), path);
    }

    public void addLeafValues(long count) {
        addLeafValues(count, rootPath);
    }

    public void addLeafValues(long count, String path) {
        leafValues = add("maximumLeafValues", leafValues, count,
                budget.maximumLeafValues(), path);
    }

    public void addEstimatedBytes(long count) {
        addEstimatedBytes(count, rootPath);
    }

    public void addEstimatedBytes(long count, String path) {
        estimatedBytes = add("maximumEstimatedAllocationBytes", estimatedBytes, count,
                budget.maximumEstimatedAllocationBytes(), path);
    }

    public void addListAllocation(int count, String path) {
        if (count < 0) throw RuntimeFailures.internalInvariant(
                "negative_materialization_list", path, "materialize");
        addEstimatedBytes(safeAdd(40L, safeMultiply(8L, count, path), path), path);
    }

    public void addOptionalAllocation(boolean present, String path) {
        if (present) addEstimatedBytes(16L, path);
    }

    public void addMapAllocation(int count, boolean boxedPrimitiveKeys, String path) {
        if (count < 0) throw RuntimeFailures.internalInvariant(
                "negative_materialization_map", path, "materialize");
        long bytes = 48L;
        if (count > 0) {
            int buckets = 16;
            long required = ((long) count * 4L + 2L) / 3L;
            while ((long) buckets < required) {
                if (buckets > (1 << 29)) {
                    throw RuntimeFailures.materializationBudgetExceeded(
                            "maximumEstimatedAllocationBytes",
                            budget.maximumEstimatedAllocationBytes(), estimatedBytes,
                            Long.MAX_VALUE, budget.identity(), path);
                }
                buckets <<= 1;
            }
            bytes = safeAdd(bytes, safeAdd(16L, safeMultiply(8L, buckets, path), path), path);
            bytes = safeAdd(bytes, safeMultiply(32L, count, path), path);
            if (boxedPrimitiveKeys) {
                bytes = safeAdd(bytes, safeMultiply(16L, count, path), path);
            }
        }
        addEstimatedBytes(bytes, path);
    }

    /** Enters one table identity during recursive accounting and rejects corrupted cycles. */
    public void enterOwnership(Object identity, String path) {
        Object required = Objects.requireNonNull(identity, "identity");
        for (int i = 0; i < ownershipStackSize; i++) {
            if (ownershipStack[i] == required) {
                throw RuntimeFailures.ownershipCycle(path, "materialize");
            }
        }
        if (ownershipStackSize == ownershipStack.length) {
            ownershipStack = Arrays.copyOf(ownershipStack, ownershipStack.length << 1);
        }
        ownershipStack[ownershipStackSize++] = required;
    }

    public void exitOwnership(Object identity, String path) {
        if (ownershipStackSize == 0 || ownershipStack[ownershipStackSize - 1] != identity) {
            throw RuntimeFailures.internalInvariant(
                    "materialization_ownership_stack", path, "materialize");
        }
        ownershipStack[--ownershipStackSize] = null;
    }

    public String budgetIdentity() { return budget.identity(); }
    public long tableInstances() { return tableInstances; }
    public long rows() { return rows; }
    public long leafValues() { return leafValues; }
    public long estimatedBytes() { return estimatedBytes; }
    public int maximumDepth() { return maximumDepth; }

    private long add(String dimension, long current, long count, long limit, String path) {
        if (count < 0L) {
            throw RuntimeFailures.internalInvariant(
                    "negative_materialization_counter", path, "materialize");
        }
        long proposed;
        if (Long.MAX_VALUE - current < count) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    dimension, limit, current, Long.MAX_VALUE, budget.identity(), path);
        }
        proposed = current + count;
        if (proposed > limit) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    dimension, limit, current, proposed, budget.identity(), path);
        }
        return proposed;
    }

    private long safeMultiply(long unit, long count, String path) {
        if (count < 0L || (count != 0L && unit > Long.MAX_VALUE / count)) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    "maximumEstimatedAllocationBytes",
                    budget.maximumEstimatedAllocationBytes(), estimatedBytes,
                    Long.MAX_VALUE, budget.identity(), path);
        }
        return unit * count;
    }

    private long safeAdd(long left, long right, String path) {
        if (right < 0L || Long.MAX_VALUE - left < right) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    "maximumEstimatedAllocationBytes",
                    budget.maximumEstimatedAllocationBytes(), estimatedBytes,
                    Long.MAX_VALUE, budget.identity(), path);
        }
        return left + right;
    }
}
