package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded caller-participating refinement of one admitted Canonical PhysicalPlan. */
final class CanonicalParallelRowScheduler {

    private CanonicalParallelRowScheduler() {
    }

    static void prepare(CanonicalRowExecutionFrame frame) {
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        if (bound.canonical.request.mode != ExecutionRequest.Mode.PARALLEL) return;
        ForkJoinPool pool = requireAvailable(bound);
        if (frame.plan.partitions <= 1
                || frame.plan.parallelPrefixStages == 0
                || bound.root.size == 0) return;

        Range[] work = ranges(frame, frame.plan.partitions);
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean start = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        Drainer[] drainers = new Drainer[work.length - 1];
        ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[work.length - 1];
        try {
            for (int index = 0; index < tasks.length; index++) {
                drainers[index] = new Drainer(work, next, start, cancelled);
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
        if (awaitQuiescence(drainers)) throw cancelled(bound);
        Throwable failure = firstFailure(work);
        if (failure != null) {
            if (failure instanceof Error) throw (Error) failure;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw new AssertionError("parallel internal range failed", failure);
        }
        frame.parallelSource = merge(work, bound);
    }

    private static ForkJoinPool requireAvailable(
            BoundCanonicalRowOperation bound) {
        if (CallbackExecutionScope.isActive()) {
            throw SomaFailures.failure(
                    SomaFailureCode.NESTED_PARALLEL_OPERATION,
                    bound.operation,
                    "parallel terminal started inside a SOMA callback",
                    bound.provenance);
        }
        ForkJoinPool pool = bound.table.parallelExecutor();
        if (pool.isShutdown() || pool.isTerminated()) {
            throw unavailable(bound, null);
        }
        return pool;
    }

    private static Range[] ranges(
            CanonicalRowExecutionFrame frame,
            int count) {
        Range[] result = new Range[count];
        BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
        int chunkRows = bound.root.directory.chunkRows();
        int chunks = CheckedStructural.ceilChunks(bound.root.size, chunkRows);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int firstChunk = (int) ((long) chunks * ordinal / count);
            int nextChunk = (int) ((long) chunks * (ordinal + 1) / count);
            int from = firstChunk * chunkRows;
            int to = (int) Math.min(
                    (long) bound.root.size, (long) nextChunk * chunkRows);
            result[ordinal] = new Range(frame, from, to);
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
            if (drainer != null && drainer.cancelPending() && task != null) {
                task.cancel(false);
            }
        }
    }

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

    private static IntLocatorBuffer merge(
            Range[] work,
            BoundCanonicalRowOperation bound) {
        IntLocatorBuffer result = new IntLocatorBuffer(
                bound.root.size, bound.operation, bound.provenance);
        for (Range range : work) {
            for (int index = 0; index < range.output.size(); index++) {
                result.add(range.output.get(index));
            }
        }
        return result;
    }

    private static RuntimeException unavailable(
            BoundCanonicalRowOperation bound,
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

    private static RuntimeException cancelled(BoundCanonicalRowOperation bound) {
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
        private final CanonicalRowExecutionFrame frame;
        private final int from;
        private final int to;
        private final IntLocatorBuffer output;
        private volatile Throwable failure;

        Range(CanonicalRowExecutionFrame frame, int from, int to) {
            this.frame = frame;
            this.from = from;
            this.to = to;
            BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
            this.output = new IntLocatorBuffer(
                    to - from, bound.operation, bound.provenance);
        }

        void run(AtomicBoolean cancelled) {
            try {
                for (int locator = from; locator < to && !cancelled.get(); locator++) {
                    if (matchesPrefix(locator)) output.add(locator);
                }
            } catch (Throwable problem) {
                failure = problem;
                cancelled.set(true);
            }
        }

        private boolean matchesPrefix(int locator) {
            BoundCanonicalRowOperation bound = frame.plan.normalized.bound;
            for (int index = 0; index < frame.plan.parallelPrefixStages; index++) {
                CanonicalRowStage stage = frame.plan.normalized.stages.get(index);
                if (!OptimizedPredicateEvaluator.matches(
                        bound.layout,
                        stage.predicate,
                        bound.root,
                        locator,
                        frame.membership)) return false;
            }
            return true;
        }
    }
}
