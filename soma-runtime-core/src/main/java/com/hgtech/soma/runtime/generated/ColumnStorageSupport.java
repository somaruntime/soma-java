package com.hgtech.soma.runtime.generated;

/** Shared checked byte accounting for concrete flat/head-tail columns。 */
final class ColumnStorageSupport {
    private static final long REFERENCE_BYTES = 8L;

    private ColumnStorageSupport() {
    }

    static long retainedBytes(
            GeneratedColumn column,
            int capacity,
            int elementBytes) {
        long payload = checkedMultiply(
                column.headCapacityFor(capacity), elementBytes);
        int tails = column.tailCountFor(capacity);
        payload = checkedAdd(
                payload,
                checkedMultiply(
                        checkedMultiply(tails, column.segmentRows()),
                        elementBytes));
        return checkedAdd(payload, checkedMultiply(tails, REFERENCE_BYTES));
    }

    static long stagingAllocationBytes(
            GeneratedColumn column,
            int oldHeadCapacity,
            int oldTailCount,
            int newCapacity,
            int elementBytes) {
        int newHead = column.headCapacityFor(newCapacity);
        int newTails = column.tailCountFor(newCapacity);
        long bytes = newHead > oldHeadCapacity
                ? checkedMultiply(newHead, elementBytes) : 0L;
        if (newTails > oldTailCount) {
            bytes = checkedAdd(
                    bytes, checkedMultiply(newTails, REFERENCE_BYTES));
            bytes = checkedAdd(
                    bytes,
                    checkedMultiply(
                            checkedMultiply(
                                    newTails - oldTailCount,
                                    column.segmentRows()),
                            elementBytes));
        }
        return bytes;
    }

    static long replacementTransientBytes(
            GeneratedColumn column,
            int oldHeadCapacity,
            int oldTailCount,
            int newCapacity,
            int elementBytes) {
        long bytes = column.headCapacityFor(newCapacity) > oldHeadCapacity
                ? checkedMultiply(oldHeadCapacity, elementBytes) : 0L;
        if (column.tailCountFor(newCapacity) > oldTailCount) {
            bytes = checkedAdd(
                    bytes, checkedMultiply(oldTailCount, REFERENCE_BYTES));
        }
        return bytes;
    }

    static long checkedAdd(long left, long right) {
        return left < 0L || right < 0L || Long.MAX_VALUE - left < right
                ? Long.MAX_VALUE : left + right;
    }

    static long checkedMultiply(long left, long right) {
        return left < 0L || right < 0L
                || left != 0L && right > Long.MAX_VALUE / left
                ? Long.MAX_VALUE : left * right;
    }
}
