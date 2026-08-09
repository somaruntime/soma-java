package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded caller-participating scheduler for canonical Table locator ranges. */
final class ParallelRowScheduler {

    private ParallelRowScheduler() {
    }

    static BoundRowPlan prepare(BoundRowPlan bound) {
        if (!bound.logical.isParallel()) return bound;
        if (CallbackExecutionScope.isActive()) {
            throw SomaFailures.failure(
                    SomaFailureCode.NESTED_PARALLEL_OPERATION,
                    bound.operation,
                    "parallel terminal started inside a SOMA callback",
                    bound.provenance);
        }

        ForkJoinPool pool = bound.logical.owner().parallelExecutor();
        if (pool.isShutdown() || pool.isTerminated()) {
            throw unavailable(bound, null);
        }

        NormalizedRowPlan plan = RowOptimizer.optimize(bound);
        if (plan.sourceKind != NormalizedRowPlan.SourceKind.TABLE_SCAN
                || bound.root.size == 0L) {
            return bound;
        }

        int participants = Math.max(1, pool.getParallelism());
        long chunks = 1L + (bound.root.size - 1L)
                / bound.root.directory.chunkRows();
        int ranges = (int) Math.min((long) participants, chunks);
        if (ranges <= 1) return bound;

        Range[] work = ranges(bound, plan, ranges);
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean start = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Drainer[] drainers = new Drainer[ranges - 1];
        ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[ranges - 1];

        try {
            for (int index = 0; index < tasks.length; index++) {
                drainers[index] = new Drainer(
                        work, next, start, cancelled);
                tasks[index] = pool.submit(drainers[index]);
            }
        } catch (RejectedExecutionException failure) {
            cancelled.set(true);
            start.set(true);
            cancelPending(drainers, tasks);
            awaitQuiescence(drainers);
            throw unavailable(bound, failure);
        }

        start.set(true);
        drain(work, next, cancelled);
        cancelPending(drainers, tasks);
        if (awaitQuiescence(drainers)) {
            throw cancelled(bound);
        }
        Throwable failure = firstFailure(work);
        if (failure != null) {
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new AssertionError("parallel internal range failed", failure);
        }
        return bound.withParallelSource(merge(work, bound));
    }

    private static Range[] ranges(
            BoundRowPlan bound,
            NormalizedRowPlan plan,
            int count) {
        Range[] result = new Range[count];
        int chunkRows = bound.root.directory.chunkRows();
        long chunks = 1L + (bound.root.size - 1L) / chunkRows;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            long firstChunk = chunks * ordinal / count;
            long nextChunk = chunks * (ordinal + 1L) / count;
            long from = firstChunk * chunkRows;
            long to = Math.min(bound.root.size, nextChunk * chunkRows);
            result[ordinal] = new Range(bound, plan, from, to);
        }
        return result;
    }

    private static void drain(
            Range[] work,
            AtomicInteger next,
            AtomicBoolean cancelled) {
        while (!cancelled.get()) {
            int ordinal = next.getAndIncrement();
            if (ordinal >= work.length) return;
            work[ordinal].run(cancelled);
        }
    }

    private static void cancelPending(
            Drainer[] drainers,
            ForkJoinTask<?>[] tasks) {
        for (int index = 0; index < drainers.length; index++) {
            Drainer drainer = drainers[index];
            ForkJoinTask<?> task = tasks[index];
            if (drainer != null && drainer.cancelPending()
                    && task != null) {
                task.cancel(false);
            }
        }
    }

    /** Returns true when the calling thread was interrupted. */
    private static boolean awaitQuiescence(Drainer[] drainers) {
        boolean interrupted = Thread.interrupted();
        while (!allQuiescent(drainers)) {
            if (Thread.interrupted()) interrupted = true;
            Thread.yield();
        }
        if (interrupted) Thread.currentThread().interrupt();
        return interrupted;
    }

    private static boolean allQuiescent(Drainer[] drainers) {
        for (Drainer drainer : drainers) {
            if (drainer != null && drainer.isRunning()) return false;
        }
        return true;
    }

    private static Throwable firstFailure(Range[] work) {
        for (Range range : work) {
            if (range.failure != null) return range.failure;
        }
        return null;
    }

    private static LongLocatorBuffer merge(Range[] work, BoundRowPlan bound) {
        LongLocatorBuffer result = new LongLocatorBuffer(
                bound.root.size, bound.operation, bound.provenance);
        for (Range range : work) {
            for (int index = 0; index < range.output.size(); index++) {
                result.add(range.output.get(index));
            }
        }
        return result;
    }

    private static RuntimeException unavailable(
            BoundRowPlan bound,
            Throwable cause) {
        return cause == null
                ? SomaFailures.failure(
                        SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                        bound.operation,
                        "parallel ForkJoinPool is unavailable",
                        bound.provenance)
                : SomaFailures.failure(
                        SomaFailureCode.PARALLEL_EXECUTOR_UNAVAILABLE,
                        bound.operation,
                        "parallel ForkJoinPool rejected a SOMA drainer",
                        cause,
                        bound.provenance);
    }

    private static RuntimeException cancelled(BoundRowPlan bound) {
        return SomaFailures.failure(
                SomaFailureCode.OPERATION_CANCELLED,
                bound.operation,
                "parallel operation was interrupted",
                bound.provenance);
    }

    private static final class Drainer implements Runnable {
        private static final int PENDING = 0;
        private static final int RUNNING = 1;
        private static final int DONE = 2;
        private static final int CANCELLED = 3;

        private final Range[] work;
        private final AtomicInteger next;
        private final AtomicBoolean start;
        private final AtomicBoolean cancelled;
        private final AtomicInteger lifecycle = new AtomicInteger(PENDING);

        Drainer(
                Range[] work,
                AtomicInteger next,
                AtomicBoolean start,
                AtomicBoolean cancelled) {
            this.work = work;
            this.next = next;
            this.start = start;
            this.cancelled = cancelled;
        }

        @Override public void run() {
            if (!lifecycle.compareAndSet(PENDING, RUNNING)) return;
            try {
                while (!start.get()) Thread.yield();
                drain(work, next, cancelled);
            } finally {
                lifecycle.set(DONE);
            }
        }

        boolean cancelPending() {
            return lifecycle.compareAndSet(PENDING, CANCELLED);
        }

        boolean isRunning() {
            return lifecycle.get() == RUNNING;
        }
    }

    private static final class Range {
        private final BoundRowPlan bound;
        private final NormalizedRowPlan plan;
        private final long from;
        private final long to;
        private final LongLocatorBuffer output;
        private volatile Throwable failure;

        Range(
                BoundRowPlan bound,
                NormalizedRowPlan plan,
                long from,
                long to) {
            this.bound = bound;
            this.plan = plan;
            this.from = from;
            this.to = to;
            this.output = new LongLocatorBuffer(
                    to - from, bound.operation, bound.provenance);
        }

        void run(AtomicBoolean cancelled) {
            try {
                for (long locator = from; locator < to && !cancelled.get(); locator++) {
                    if (matchesTypedPrefix(locator)) output.add(locator);
                }
            } catch (Throwable problem) {
                failure = problem;
                cancelled.set(true);
            }
        }

        private boolean matchesTypedPrefix(long locator) {
            for (LogicalRowPlan.Stage stage : plan.stages) {
                if (stage.kind != LogicalRowPlan.StageKind.TYPED_FILTER) break;
                if (!OptimizedPredicateEvaluator.matches(
                        bound.logical.owner().layout(),
                        stage.predicate,
                        bound.root,
                        locator,
                        plan.membership)) {
                    return false;
                }
            }
            return true;
        }
    }
}
