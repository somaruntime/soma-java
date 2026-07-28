package io.github.somaruntime.soma.dataflow;

/**
 * Versioned deterministic formula for bounded data-parallel morsels.
 */
public final class MorselSchedulerFormula {
    public static final String IDENTITY = "soma-morsel-scheduler-v1";
    static final int MAX_TASKS_PER_WORKER = 4;
    private static final int MINIMUM_MORSEL_ROWS = 1_024;

    private MorselSchedulerFormula() {
    }

    static int targetRows(
            int minimumParallelCardinality,
            int storageSegmentRows,
            int workers) {
        int byActivation = Math.max(
                MINIMUM_MORSEL_ROWS,
                ceilDivide(minimumParallelCardinality, workers));
        if (storageSegmentRows <= 0) {
            return byActivation;
        }
        int bySegment = Math.max(
                MINIMUM_MORSEL_ROWS,
                ceilDivide(storageSegmentRows, workers));
        return Math.min(byActivation, bySegment);
    }

    private static int ceilDivide(int value, int divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }
}
