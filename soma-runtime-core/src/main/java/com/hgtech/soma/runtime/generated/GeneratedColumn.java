package com.hgtech.soma.runtime.generated;

import com.hgtech.soma.runtime.TablePlan;
import com.hgtech.soma.runtime.metadata.SomaStorageLayout;

/**
 * 供 generated storage binding 使用的 column capacity 协议。
 */
public abstract class GeneratedColumn {
    private SomaStorageLayout storageLayout = SomaStorageLayout.FLAT;
    private int flatHeadRows;
    private int segmentRows;
    private boolean configured;

    GeneratedColumn() {
    }

    final void configure(TablePlan plan) {
        if (configured) {
            throw new IllegalStateException("column layout already configured");
        }
        if (plan == null) throw new NullPointerException("plan");
        storageLayout = plan.storageLayout();
        flatHeadRows = plan.flatHeadRows();
        segmentRows = plan.segmentRows();
        if (storageLayout == SomaStorageLayout.FLAT) {
            if (flatHeadRows != 0 || segmentRows != 0) {
                throw new IllegalArgumentException(
                        "flat layout cannot declare segments");
            }
        } else if (flatHeadRows <= 0 || segmentRows <= 0
                || (segmentRows & (segmentRows - 1)) != 0
                || flatHeadRows != segmentRows) {
            throw new IllegalArgumentException(
                    "invalid flat-head segmented-tail layout");
        }
        configured = true;
    }

    final boolean segmented() {
        return storageLayout == SomaStorageLayout.FLAT_HEAD_SEGMENTED_TAIL;
    }

    final int flatHeadRows() {
        return flatHeadRows;
    }

    final int segmentRows() {
        return segmentRows;
    }

    final int segmentShift() {
        return Integer.numberOfTrailingZeros(segmentRows());
    }

    final int headCapacityFor(int capacity) {
        requireCapacity(capacity);
        return segmented() ? Math.min(capacity, flatHeadRows) : capacity;
    }

    final int tailCountFor(int capacity) {
        requireCapacity(capacity);
        if (!segmented() || capacity <= flatHeadRows) return 0;
        long rows = (long) capacity - (long) flatHeadRows;
        return (int) ((rows + segmentRows - 1L) / segmentRows);
    }

    final int physicalCapacityFor(int required) {
        requireCapacity(required);
        if (!segmented() || required <= flatHeadRows) return required;
        long tails = tailCountFor(required);
        long capacity = (long) flatHeadRows + tails * (long) segmentRows;
        if (capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "segmented capacity exceeds Java row boundary");
        }
        return (int) capacity;
    }

    final int tailOrdinal(int rowIndex) {
        return (rowIndex - flatHeadRows()) >>> segmentShift();
    }

    final int tailOffset(int rowIndex) {
        return (rowIndex - flatHeadRows()) & (segmentRows() - 1);
    }

    final int capacityOf(int headCapacity, int tailCount) {
        long capacity = (long) headCapacity
                + (long) tailCount * (long) segmentRows;
        if (capacity < 0L || capacity > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "column capacity exceeds Java row boundary");
        }
        return (int) capacity;
    }

    public abstract Object stageCapacity(int newCapacity);

    public abstract void commitCapacity(Object stagedCapacity);

    public abstract void clearRange(int fromInclusive, int toExclusive);

    abstract long estimatedBytes(int capacity);

    abstract long stagingAllocationBytes(int newCapacity);

    abstract long replacementTransientBytes(int newCapacity);

    abstract long retainedBytes();

    abstract void releaseStorage();

    static void requireCapacity(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative");
        }
    }


    static void requireGrowth(int currentCapacity, int newCapacity) {
        requireCapacity(newCapacity);
        if (newCapacity < currentCapacity) {
            throw new IllegalArgumentException("capacity shrink is not supported");
        }
    }

    static void requireRange(int fromInclusive, int toExclusive, int capacity) {
        if (fromInclusive < 0 || toExclusive < fromInclusive || toExclusive > capacity) {
            throw new IndexOutOfBoundsException("invalid range");
        }
    }

    static void requireCopyRange(int sourceIndex, int targetIndex, int length,
                                 int sourceCapacity, int targetCapacity) {
        if (sourceIndex < 0 || targetIndex < 0 || length < 0
                || length > sourceCapacity - sourceIndex
                || length > targetCapacity - targetIndex) {
            throw new IndexOutOfBoundsException("invalid copy range");
        }
    }
}
