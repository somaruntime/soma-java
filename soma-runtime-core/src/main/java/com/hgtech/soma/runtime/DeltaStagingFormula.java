package com.hgtech.soma.runtime;

/**
 * Versioned deterministic crossover for generated keyed Delta staging.
 */
public final class DeltaStagingFormula {
    public static final String IDENTITY = "soma-delta-staging-v1";
    private static final int MINIMUM_CHANGED_ROWS = 64;
    private static final int TABLE_FRACTION_SHIFT = 3;

    private DeltaStagingFormula() {
    }

    /**
     * Uses changed-row staging while the Delta is no larger than the greater
     * of 64 rows or one eighth of the current Table.
     */
    public static boolean useChangedRows(int tableRows, int changedRows) {
        if (tableRows < 0 || changedRows < 0) {
            throw new IllegalArgumentException(
                    "tableRows and changedRows must be non-negative");
        }
        return changedRows <= Math.max(
                MINIMUM_CHANGED_ROWS, tableRows >>> TABLE_FRACTION_SHIFT);
    }
}
