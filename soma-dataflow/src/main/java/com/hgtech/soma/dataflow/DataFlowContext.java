package com.hgtech.soma.dataflow;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Execution resource owner.
 *
 * <p>The context owns policy, budget and optionally a dedicated executor. It never
 * owns a Table, Definition or source registry.</p>
 */
public final class DataFlowContext implements AutoCloseable {
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final int workers;
    private final ExecutionPolicy defaultPolicy;
    private final ExecutionBudget budgetUpperBound;
    private boolean closed;
    private int activeInvocations;

    private DataFlowContext(
            ExecutorService executor,
            boolean ownsExecutor,
            int workers,
            ExecutionPolicy defaultPolicy,
            ExecutionBudget budgetUpperBound) {
        this.executor = executor;
        this.ownsExecutor = ownsExecutor;
        this.workers = workers;
        this.defaultPolicy = Objects.requireNonNull(defaultPolicy, "defaultPolicy");
        this.budgetUpperBound = Objects.requireNonNull(
                budgetUpperBound, "budgetUpperBound");
        if (workers <= 0 || workers > budgetUpperBound.maximumWorkers()) {
            throw new IllegalArgumentException("workers outside budget upper bound");
        }
    }

    public static DataFlowContext sequential() {
        return new DataFlowContext(
                null, false, 1, ExecutionPolicy.sequential(), ExecutionBudget.defaults());
    }

    public static DataFlowContext managedParallel(int workers) {
        return managedParallel(
                workers, ExecutionPolicy.adaptiveParallel(), ExecutionBudget.defaults());
    }

    public static DataFlowContext managedParallel(
            int workers, ExecutionPolicy policy, ExecutionBudget budgetUpperBound) {
        requireArguments(workers, policy, budgetUpperBound);
        ForkJoinPool pool = new ForkJoinPool(workers);
        try {
            return new DataFlowContext(
                    pool, true, workers, policy, budgetUpperBound);
        } catch (RuntimeException failure) {
            pool.shutdownNow();
            throw failure;
        } catch (Error failure) {
            pool.shutdownNow();
            throw failure;
        }
    }

    public static DataFlowContext borrowed(
            ExecutorService executor, int workers) {
        return borrowed(
                executor, workers, ExecutionPolicy.adaptiveParallel(),
                ExecutionBudget.defaults());
    }

    public static DataFlowContext borrowed(
            ExecutorService executor,
            int workers,
            ExecutionPolicy policy,
            ExecutionBudget budgetUpperBound) {
        requireArguments(workers, policy, budgetUpperBound);
        return new DataFlowContext(
                Objects.requireNonNull(executor, "executor"),
                false,
                workers,
                policy,
                budgetUpperBound);
    }

    private static void requireArguments(
            int workers,
            ExecutionPolicy policy,
            ExecutionBudget budgetUpperBound) {
        if (workers <= 0) {
            throw new IllegalArgumentException(
                    "workers must be positive");
        }
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(
                budgetUpperBound, "budgetUpperBound");
        if (workers > budgetUpperBound.maximumWorkers()) {
            throw new IllegalArgumentException(
                    "workers outside budget upper bound");
        }
    }

    public synchronized boolean ownsExecutor() {
        return ownsExecutor;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public int workers() {
        return workers;
    }

    ExecutionPolicy defaultPolicy() {
        return defaultPolicy;
    }

    ExecutionBudget budgetUpperBound() {
        return budgetUpperBound;
    }

    ExecutorService executor() {
        return executor;
    }

    synchronized void beginInvocation() {
        if (closed) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_context_closed", "context", "dataflow.execute", "CLOSED");
        }
        activeInvocations++;
    }

    synchronized void endInvocation() {
        if (activeInvocations <= 0) {
            throw DataFlowFailures.internal(
                    "dataflow_context_accounting", "context",
                    "dataflow.cleanup", "active invocation underflow");
        }
        activeInvocations--;
    }

    @Override
    public synchronized void close() {
        if (activeInvocations != 0) {
            throw DataFlowFailures.conflict(
                    "dataflow_context_busy", "context", "dataflow.context.close",
                    Integer.toString(activeInvocations));
        }
        if (closed) {
            return;
        }
        closed = true;
        if (!ownsExecutor) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                throw DataFlowFailures.resource(
                        "dataflow_executor_not_quiescent", "context",
                        "dataflow.context.close", "managed executor");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw DataFlowFailures.callback(
                    "dataflow_context_close_interrupted", "context",
                    "dataflow.context.close", failure);
        }
    }
}
