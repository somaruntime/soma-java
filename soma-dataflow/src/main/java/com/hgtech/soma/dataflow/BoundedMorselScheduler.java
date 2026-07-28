package com.hgtech.soma.dataflow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Immutable logical-ordinal morsel plan. */
final class MorselPlan {
    private static final MorselPlan SEQUENTIAL =
            new MorselPlan(0, 1, 1, 0L);

    private final int cardinality;
    private final int tasks;
    private final int workers;
    private final long perWorkerScratchBytes;

    private MorselPlan(
            int cardinality,
            int tasks,
            int workers,
            long perWorkerScratchBytes) {
        this.cardinality = cardinality;
        this.tasks = tasks;
        this.workers = workers;
        this.perWorkerScratchBytes = perWorkerScratchBytes;
    }

    static MorselPlan sequential() {
        return SEQUENTIAL;
    }

    static MorselPlan create(
            int cardinality,
            int tasks,
            int workers,
            long perWorkerScratchBytes) {
        return new MorselPlan(
                cardinality, tasks, workers, perWorkerScratchBytes);
    }

    boolean parallel() {
        return tasks > 1;
    }

    int tasks() {
        return tasks;
    }

    int workers() {
        return workers;
    }

    long perWorkerScratchBytes() {
        return perWorkerScratchBytes;
    }

    int start(int partition) {
        int base = cardinality / tasks;
        int remainder = cardinality % tasks;
        return partition * base + Math.min(partition, remainder);
    }

    int end(int partition) {
        return start(partition + 1);
    }
}

interface MorselWork<T> {
    T execute(int partition, int startInclusive, int endExclusive);
}

/**
 * The sole bounded scheduler for branch and row data parallelism.
 */
final class BoundedMorselScheduler {
    private BoundedMorselScheduler() {
    }

    static MorselPlan plan(
            ExecutionFrame frame,
            int cardinality,
            boolean eligible,
            long perWorkerScratchBytes,
            int taskWaves,
            int storageSegmentRows,
            String operation) {
        if (!eligible
                || cardinality < frame.policy().minimumParallelCardinality()
                || frame.policy().mode() != ExecutionPolicy.Mode.ADAPTIVE_PARALLEL
                || frame.context().workers() <= 1
                || frame.context().executor() == null) {
            return MorselPlan.sequential();
        }
        if (perWorkerScratchBytes < 0L
                || perWorkerScratchBytes
                > frame.budget().maximumWorkerScratchBytes()) {
            return MorselPlan.sequential();
        }
        int workerLimit = Math.min(
                frame.context().workers(),
                frame.budget().maximumWorkers());
        int taskLimit = frame.budget().maximumTasks()
                / Math.max(1, taskWaves);
        int targetRows = MorselSchedulerFormula.targetRows(
                frame.policy().minimumParallelCardinality(),
                storageSegmentRows,
                workerLimit);
        int desiredTasks = cardinality / targetRows
                + (cardinality % targetRows == 0 ? 0 : 1);
        desiredTasks = Math.max(2, desiredTasks);
        int waveLimit = workerLimit
                > Integer.MAX_VALUE
                / MorselSchedulerFormula.MAX_TASKS_PER_WORKER
                ? Integer.MAX_VALUE
                : workerLimit
                * MorselSchedulerFormula.MAX_TASKS_PER_WORKER;
        desiredTasks = Math.min(desiredTasks, waveLimit);
        int tasks = Math.min(cardinality, Math.min(taskLimit, desiredTasks));
        int workers = Math.min(workerLimit, tasks);
        if (tasks <= 1 || workers <= 1) {
            return MorselPlan.sequential();
        }
        frame.checkBoundary(operation);
        return MorselPlan.create(
                cardinality, tasks, workers, perWorkerScratchBytes);
    }

    static <T> List<T> run(
            final ExecutionFrame frame,
            final MorselPlan plan,
            final MorselWork<T> work,
            final String operation) {
        if (!plan.parallel()) {
            throw new IllegalArgumentException("parallel plan required");
        }
        InvocationPhaseLease lease = frame.beginParallel(
                plan.tasks(),
                plan.workers(),
                plan.perWorkerScratchBytes(),
                operation);
        try {
            return runLeased(frame, plan, work, operation);
        } finally {
            lease.close();
        }
    }

    private static <T> List<T> runLeased(
            final ExecutionFrame frame,
            final MorselPlan plan,
            final MorselWork<T> work,
            final String operation) {
        List<Callable<T>> tasks =
                new ArrayList<Callable<T>>(plan.tasks());
        for (int partition = 0; partition < plan.tasks(); partition++) {
            final int ordinal = partition;
            tasks.add(new Callable<T>() {
                @Override
                public T call() {
                    frame.checkBoundary(operation);
                    return work.execute(
                            ordinal,
                            plan.start(ordinal),
                            plan.end(ordinal));
                }
            });
        }

        final List<Future<T>> futures;
        try {
            futures = frame.context().executor().invokeAll(tasks);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw DataFlowFailures.lifecycle(
                    "dataflow_parallel_interrupted",
                    "invocation.parallel",
                    operation,
                    "INTERRUPTED");
        } catch (RejectedExecutionException failure) {
            throw DataFlowFailures.resource(
                    "dataflow_executor_rejected",
                    "invocation.parallel",
                    operation,
                    failure.getClass().getName());
        }

        List<T> result = new ArrayList<T>(plan.tasks());
        Throwable primary = null;
        for (int partition = 0; partition < futures.size(); partition++) {
            try {
                result.add(futures.get(partition).get());
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                cancel(futures);
                throw DataFlowFailures.lifecycle(
                        "dataflow_parallel_interrupted",
                        "invocation.parallel",
                        operation,
                        "INTERRUPTED");
            } catch (ExecutionException failure) {
                result.add(null);
                Throwable cause = failure.getCause() == null
                        ? failure : failure.getCause();
                if (primary == null) {
                    primary = cause;
                } else {
                    primary.addSuppressed(cause);
                }
            }
        }
        if (primary != null) {
            rethrow(primary, operation);
        }
        return result;
    }

    private static void cancel(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    private static void rethrow(Throwable failure, String operation) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw DataFlowFailures.internal(
                "dataflow_parallel_checked_failure",
                "invocation.parallel",
                operation,
                failure.getClass().getName());
    }
}
