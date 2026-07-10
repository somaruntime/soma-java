package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.MaterializationBudget;

import java.util.Objects;

/** 单次 materialization invocation 的 overflow-safe deterministic budget tracker。 */
public final class MaterializationTracker {
    private final MaterializationBudget budget;
    private final String rootPath;
    private long tableInstances;
    private long rows;
    private long leafValues;
    private long estimatedBytes;

    public MaterializationTracker(MaterializationBudget budget, String rootPath) {
        this.budget = Objects.requireNonNull(budget, "budget");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
    }

    public void addTableInstances(long count) {
        tableInstances = add("maximumTableInstances", tableInstances, count,
                budget.maximumTableInstances());
    }

    public void addRows(long count) {
        rows = add("maximumRows", rows, count, budget.maximumRows());
    }

    public void addLeafValues(long count) {
        leafValues = add("maximumLeafValues", leafValues, count,
                budget.maximumLeafValues());
    }

    public void addEstimatedBytes(long count) {
        estimatedBytes = add("maximumEstimatedAllocationBytes", estimatedBytes, count,
                budget.maximumEstimatedAllocationBytes());
    }

    public String budgetIdentity() { return budget.identity(); }
    public long tableInstances() { return tableInstances; }
    public long rows() { return rows; }
    public long leafValues() { return leafValues; }
    public long estimatedBytes() { return estimatedBytes; }

    private long add(String dimension, long current, long count, long limit) {
        if (count < 0L) {
            throw RuntimeFailures.internalInvariant(
                    "negative_materialization_counter", rootPath, "materialize");
        }
        long proposed;
        if (Long.MAX_VALUE - current < count) {
            proposed = Long.MAX_VALUE;
        } else {
            proposed = current + count;
        }
        if (proposed > limit || proposed < current) {
            throw RuntimeFailures.materializationBudgetExceeded(
                    dimension, limit, current, proposed, budget.identity(), rootPath);
        }
        return proposed;
    }
}
