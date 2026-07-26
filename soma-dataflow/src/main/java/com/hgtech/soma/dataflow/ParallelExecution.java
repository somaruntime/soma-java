package com.hgtech.soma.dataflow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Deterministic contiguous-partition execution support. */
final class ParallelPlan {
    private static final ParallelPlan SEQUENTIAL =
            new ParallelPlan(0, 1);

    private final int cardinality;
    private final int tasks;

    private ParallelPlan(int cardinality, int tasks) {
        this.cardinality = cardinality;
        this.tasks = tasks;
    }

    static ParallelPlan sequential() {
        return SEQUENTIAL;
    }

    static ParallelPlan create(int cardinality, int tasks) {
        return new ParallelPlan(cardinality, tasks);
    }

    boolean parallel() {
        return tasks > 1;
    }

    int tasks() {
        return tasks;
    }

    int workers() {
        return tasks;
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

interface ParallelWork<T> {
    T execute(int partition, int startInclusive, int endExclusive);
}

final class ParallelExecution {
    private ParallelExecution() {
    }

    static ParallelPlan plan(
            ExecutionFrame frame,
            int cardinality,
            boolean eligible,
            long perWorkerScratchBytes,
            int taskWaves,
            String operation) {
        if (!eligible
                || cardinality < frame.policy().minimumParallelCardinality()
                || frame.policy().mode() != ExecutionPolicy.Mode.ADAPTIVE_PARALLEL
                || frame.context().workers() <= 1
                || frame.context().executor() == null) {
            return ParallelPlan.sequential();
        }
        if (perWorkerScratchBytes < 0L
                || perWorkerScratchBytes
                > frame.budget().maximumWorkerScratchBytes()) {
            return ParallelPlan.sequential();
        }
        int tasks = Math.min(
                cardinality,
                Math.min(
                        frame.context().workers(),
                        Math.min(
                                frame.budget().maximumWorkers(),
                                frame.budget().maximumTasks()
                                        / Math.max(1, taskWaves))));
        if (tasks <= 1) {
            return ParallelPlan.sequential();
        }
        frame.checkBoundary(operation);
        return ParallelPlan.create(cardinality, tasks);
    }

    static <T> List<T> run(
            final ExecutionFrame frame,
            final ParallelPlan plan,
            final ParallelWork<T> work,
            final String operation) {
        if (!plan.parallel()) {
            throw new IllegalArgumentException("parallel plan required");
        }
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
