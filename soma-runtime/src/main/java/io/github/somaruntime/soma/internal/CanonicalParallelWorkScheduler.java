package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Shared bounded lifecycle for admitted ordinal work. */
final class CanonicalParallelWorkScheduler {

    interface Work {
        void run(int ordinal, AtomicBoolean cancelled);
    }

    private CanonicalParallelWorkScheduler() {
    }

    static void validate(BoundCanonicalRowOperation bound) {
        requireAvailable(bound);
    }

    static void execute(
            BoundCanonicalRowOperation bound,
            int units,
            Work work) {
        if (units < 0 || work == null) {
            throw new AssertionError("invalid parallel ordinal work");
        }
        ForkJoinPool pool = requireAvailable(bound);
        if (units == 0) return;
        int participants = Math.min(
                Math.max(1, pool.getParallelism()), units);
        if (participants == 1) {
            AtomicBoolean cancelled = new AtomicBoolean();
            for (int ordinal = 0; ordinal < units; ordinal++) {
                work.run(ordinal, cancelled);
            }
            return;
        }

        AtomicInteger next = new AtomicInteger();
        AtomicBoolean start = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Failure> failure = new AtomicReference<Failure>();
        Drainer[] drainers = new Drainer[participants - 1];
        ForkJoinTask<?>[] tasks = new ForkJoinTask<?>[participants - 1];
        try {
            for (int index = 0; index < tasks.length; index++) {
                drainers[index] = new Drainer(
                        units, work, next, start, cancelled, failure);
                tasks[index] = pool.submit(drainers[index]);
            }
        } catch (RejectedExecutionException rejected) {
            cancelled.set(true);
            start.set(true);
            cancelPending(drainers, tasks);
            awaitQuiescence(drainers);
            throw unavailable(bound, rejected);
        }

        start.set(true);
        drain(units, work, next, cancelled, failure);
        cancelPending(drainers, tasks);
        if (awaitQuiescence(drainers)) throw cancelled(bound);
        Failure problem = failure.get();
        if (problem != null) propagate(problem.problem);
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

    private static void drain(
            int units,
            Work work,
            AtomicInteger next,
            AtomicBoolean cancelled,
            AtomicReference<Failure> failure) {
        while (!cancelled.get()) {
            int ordinal = next.getAndIncrement();
            if (ordinal >= units) return;
            try {
                work.run(ordinal, cancelled);
            } catch (Throwable problem) {
                recordFailure(failure, ordinal, problem);
                cancelled.set(true);
            }
        }
    }

    private static void recordFailure(
            AtomicReference<Failure> target,
            int ordinal,
            Throwable problem) {
        Failure candidate = new Failure(ordinal, problem);
        while (true) {
            Failure current = target.get();
            if (current != null && current.ordinal <= ordinal) return;
            if (target.compareAndSet(current, candidate)) return;
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

    private static RuntimeException cancelled(
            BoundCanonicalRowOperation bound) {
        return SomaFailures.failure(
                SomaFailureCode.OPERATION_CANCELLED,
                bound.operation,
                "parallel operation was interrupted",
                bound.provenance);
    }

    private static void propagate(Throwable problem) {
        if (problem instanceof Error) throw (Error) problem;
        if (problem instanceof RuntimeException) {
            throw (RuntimeException) problem;
        }
        throw new AssertionError("parallel internal work failed", problem);
    }

    private static final class Failure {
        final int ordinal;
        final Throwable problem;

        Failure(int ordinal, Throwable problem) {
            this.ordinal = ordinal;
            this.problem = problem;
        }
    }

    private static final class Drainer implements Runnable {
        private static final int PENDING = 0;
        private static final int RUNNING = 1;
        private static final int DONE = 2;
        private static final int CANCELLED = 3;

        private final int units;
        private final Work work;
        private final AtomicInteger next;
        private final AtomicBoolean start;
        private final AtomicBoolean cancelled;
        private final AtomicReference<Failure> failure;
        private final AtomicInteger lifecycle = new AtomicInteger(PENDING);

        Drainer(
                int units,
                Work work,
                AtomicInteger next,
                AtomicBoolean start,
                AtomicBoolean cancelled,
                AtomicReference<Failure> failure) {
            this.units = units;
            this.work = work;
            this.next = next;
            this.start = start;
            this.cancelled = cancelled;
            this.failure = failure;
        }

        @Override public void run() {
            if (!lifecycle.compareAndSet(PENDING, RUNNING)) return;
            try {
                while (!start.get()) Thread.yield();
                drain(units, work, next, cancelled, failure);
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
}
