package com.hgtech.soma.dataflow;

import java.util.Objects;

/** Immutable logical execution policy; physical fallback remains planner-owned. */
public final class ExecutionPolicy {
    public enum Mode {
        SEQUENTIAL,
        ADAPTIVE_PARALLEL
    }

    private static final ExecutionPolicy SEQUENTIAL =
            new ExecutionPolicy(Mode.SEQUENTIAL, 1024, "sequential-v1");
    private static final ExecutionPolicy ADAPTIVE =
            new ExecutionPolicy(Mode.ADAPTIVE_PARALLEL, 1024, "adaptive-parallel-v1");

    private final Mode mode;
    private final int minimumParallelCardinality;
    private final String identity;

    private ExecutionPolicy(Mode mode, int minimumParallelCardinality, String identity) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (minimumParallelCardinality <= 0) {
            throw new IllegalArgumentException(
                    "minimumParallelCardinality must be positive");
        }
        this.minimumParallelCardinality = minimumParallelCardinality;
        this.identity = DataFlowSupport.required(identity, "identity");
    }

    public static ExecutionPolicy sequential() {
        return SEQUENTIAL;
    }

    public static ExecutionPolicy adaptiveParallel() {
        return ADAPTIVE;
    }

    public ExecutionPolicy withMinimumParallelCardinality(int value) {
        return new ExecutionPolicy(mode, value, identity);
    }

    public Mode mode() {
        return mode;
    }

    public int minimumParallelCardinality() {
        return minimumParallelCardinality;
    }

    public String identity() {
        return identity;
    }
}
