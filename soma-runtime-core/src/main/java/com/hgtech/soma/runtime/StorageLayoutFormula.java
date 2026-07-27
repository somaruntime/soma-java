package com.hgtech.soma.runtime;

import com.hgtech.soma.runtime.metadata.SomaStorageLayout;
import com.hgtech.soma.runtime.metadata.SomaWorkloadProfile;

/** Versioned, deterministic storage-layout resolution; never runs on a row hot path。 */
final class StorageLayoutFormula {
    static final String IDENTITY = "soma-storage-layout-v1";
    static final int SEGMENT_ROWS = 32768;
    private static final int BALANCED_LARGE_ROWS = 1024 * 1024;
    private static final int POINT_LARGE_ROWS = 8 * 1024 * 1024;
    private static final long BALANCED_LARGE_BYTES = 64L * 1024L * 1024L;
    private static final long POINT_LARGE_BYTES = 512L * 1024L * 1024L;

    private StorageLayoutFormula() {
    }

    static Resolution resolve(
            String identity,
            SomaWorkloadProfile workload,
            int planningRows,
            int structuralBytesPerRow) {
        if (!IDENTITY.equals(identity)) {
            throw new IllegalArgumentException(
                    "unsupported storage layout formula identity");
        }
        if (workload == null) throw new NullPointerException("workload");
        if (planningRows <= 0 || structuralBytesPerRow <= 0) {
            throw new IllegalArgumentException(
                    "layout formula inputs must be positive");
        }
        long plannedBytes = checkedMultiply(
                planningRows, structuralBytesPerRow);
        boolean segmented;
        switch (workload) {
            case SCAN_GROWTH:
                segmented = planningRows > SEGMENT_ROWS;
                break;
            case POINT_HEAVY:
                segmented = planningRows >= POINT_LARGE_ROWS
                        || plannedBytes >= POINT_LARGE_BYTES;
                break;
            default:
                segmented = planningRows >= BALANCED_LARGE_ROWS
                        || plannedBytes >= BALANCED_LARGE_BYTES;
                break;
        }
        return segmented
                ? new Resolution(
                        SomaStorageLayout.FLAT_HEAD_SEGMENTED_TAIL,
                        SEGMENT_ROWS,
                        SEGMENT_ROWS)
                : new Resolution(SomaStorageLayout.FLAT, 0, 0);
    }

    private static long checkedMultiply(int left, int right) {
        long result = (long) left * (long) right;
        return result < 0L ? Long.MAX_VALUE : result;
    }

    static final class Resolution {
        final SomaStorageLayout layout;
        final int flatHeadRows;
        final int segmentRows;

        private Resolution(
                SomaStorageLayout layout,
                int flatHeadRows,
                int segmentRows) {
            this.layout = layout;
            this.flatHeadRows = flatHeadRows;
            this.segmentRows = segmentRows;
        }
    }
}
