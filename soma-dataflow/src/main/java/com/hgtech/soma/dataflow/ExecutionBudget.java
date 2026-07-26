package com.hgtech.soma.dataflow;

/** Immutable hard resource bounds for one DataFlow Invocation. */
public final class ExecutionBudget {
    private static final ExecutionBudget DEFAULTS = builder().build();

    private final long maximumOutputElements;
    private final long maximumOutputBytes;
    private final long maximumInvocationScratchBytes;
    private final long maximumWorkerScratchBytes;
    private final int maximumTasks;
    private final int maximumWorkers;
    private final long deadlineNanos;

    private ExecutionBudget(Builder builder) {
        maximumOutputElements = builder.maximumOutputElements;
        maximumOutputBytes = builder.maximumOutputBytes;
        maximumInvocationScratchBytes = builder.maximumInvocationScratchBytes;
        maximumWorkerScratchBytes = builder.maximumWorkerScratchBytes;
        maximumTasks = builder.maximumTasks;
        maximumWorkers = builder.maximumWorkers;
        deadlineNanos = builder.deadlineNanos;
    }

    public static ExecutionBudget defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public long maximumOutputElements() {
        return maximumOutputElements;
    }

    public long maximumOutputBytes() {
        return maximumOutputBytes;
    }

    public long maximumInvocationScratchBytes() {
        return maximumInvocationScratchBytes;
    }

    public long maximumWorkerScratchBytes() {
        return maximumWorkerScratchBytes;
    }

    public int maximumTasks() {
        return maximumTasks;
    }

    public int maximumWorkers() {
        return maximumWorkers;
    }

    /** Absolute {@link System#nanoTime()} deadline, or zero when absent. */
    public long deadlineNanos() {
        return deadlineNanos;
    }

    void requireNarrowerThan(ExecutionBudget upperBound) {
        if (maximumOutputElements > upperBound.maximumOutputElements
                || maximumOutputBytes > upperBound.maximumOutputBytes
                || maximumInvocationScratchBytes
                > upperBound.maximumInvocationScratchBytes
                || maximumWorkerScratchBytes > upperBound.maximumWorkerScratchBytes
                || maximumTasks > upperBound.maximumTasks
                || maximumWorkers > upperBound.maximumWorkers
                || widensDeadline(deadlineNanos, upperBound.deadlineNanos)) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_budget_not_narrower", "invocation.budget", "budget");
        }
    }

    private static boolean widensDeadline(long value, long upper) {
        return upper != 0L && (value == 0L || value > upper);
    }

    public static final class Builder {
        private long maximumOutputElements = Integer.MAX_VALUE;
        private long maximumOutputBytes = 1024L * 1024L * 1024L;
        private long maximumInvocationScratchBytes = 256L * 1024L * 1024L;
        private long maximumWorkerScratchBytes = 64L * 1024L * 1024L;
        private int maximumTasks = 4096;
        private int maximumWorkers = 256;
        private long deadlineNanos;

        private Builder() {
        }

        private Builder(ExecutionBudget source) {
            maximumOutputElements = source.maximumOutputElements;
            maximumOutputBytes = source.maximumOutputBytes;
            maximumInvocationScratchBytes = source.maximumInvocationScratchBytes;
            maximumWorkerScratchBytes = source.maximumWorkerScratchBytes;
            maximumTasks = source.maximumTasks;
            maximumWorkers = source.maximumWorkers;
            deadlineNanos = source.deadlineNanos;
        }

        public Builder maximumOutputElements(long value) {
            maximumOutputElements = positive(value, "maximumOutputElements");
            return this;
        }

        public Builder maximumOutputBytes(long value) {
            maximumOutputBytes = positive(value, "maximumOutputBytes");
            return this;
        }

        public Builder maximumInvocationScratchBytes(long value) {
            maximumInvocationScratchBytes =
                    positive(value, "maximumInvocationScratchBytes");
            return this;
        }

        public Builder maximumWorkerScratchBytes(long value) {
            maximumWorkerScratchBytes = positive(value, "maximumWorkerScratchBytes");
            return this;
        }

        public Builder maximumTasks(int value) {
            maximumTasks = positive(value, "maximumTasks");
            return this;
        }

        public Builder maximumWorkers(int value) {
            maximumWorkers = positive(value, "maximumWorkers");
            return this;
        }

        public Builder deadlineNanos(long value) {
            if (value < 0L) {
                throw new IllegalArgumentException("deadlineNanos must be non-negative");
            }
            deadlineNanos = value;
            return this;
        }

        public ExecutionBudget build() {
            return new ExecutionBudget(this);
        }

        private static long positive(long value, String name) {
            if (value <= 0L) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }

        private static int positive(int value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
