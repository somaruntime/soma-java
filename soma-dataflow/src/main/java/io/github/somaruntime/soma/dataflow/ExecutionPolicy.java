package io.github.somaruntime.soma.dataflow;

import java.util.Objects;

/** Immutable logical execution policy; physical fallback remains planner-owned. */
public final class ExecutionPolicy {
    public enum Mode {
        SEQUENTIAL,
        ADAPTIVE_PARALLEL
    }

    private static final ExecutionPolicy SEQUENTIAL =
            new ExecutionPolicy(
                    Mode.SEQUENTIAL, 1024, StatsMode.BASIC);
    private static final ExecutionPolicy ADAPTIVE =
            new ExecutionPolicy(
                    Mode.ADAPTIVE_PARALLEL, 65536, StatsMode.BASIC);

    private final Mode mode;
    private final int minimumParallelCardinality;
    private final StatsMode statsMode;
    private final String identity;

    private ExecutionPolicy(
            Mode mode,
            int minimumParallelCardinality,
            StatsMode statsMode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (minimumParallelCardinality <= 0) {
            throw new IllegalArgumentException(
                    "minimumParallelCardinality must be positive");
        }
        this.minimumParallelCardinality = minimumParallelCardinality;
        this.statsMode = Objects.requireNonNull(statsMode, "statsMode");
        this.identity = (mode == Mode.SEQUENTIAL
                ? "sequential-v1" : "adaptive-parallel-v1")
                + "[minimum=" + minimumParallelCardinality
                + ",stats=" + statsMode + "]";
    }

    public static ExecutionPolicy sequential() {
        return SEQUENTIAL;
    }

    public static ExecutionPolicy adaptiveParallel() {
        return ADAPTIVE;
    }

    public ExecutionPolicy withMinimumParallelCardinality(int value) {
        return new ExecutionPolicy(mode, value, statsMode);
    }

    public ExecutionPolicy withStatsMode(StatsMode value) {
        return new ExecutionPolicy(
                mode,
                minimumParallelCardinality,
                Objects.requireNonNull(value, "statsMode"));
    }

    public Mode mode() {
        return mode;
    }

    public int minimumParallelCardinality() {
        return minimumParallelCardinality;
    }

    public StatsMode statsMode() {
        return statsMode;
    }

    public String identity() {
        return identity;
    }
}
