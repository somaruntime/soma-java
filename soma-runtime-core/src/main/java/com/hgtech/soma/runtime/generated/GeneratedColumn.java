package com.hgtech.soma.runtime.generated;

/**
 * 供 generated storage binding 使用的 column capacity 协议。
 */
public abstract class GeneratedColumn {

    GeneratedColumn() {
    }

    public abstract Object stageCapacity(int newCapacity);

    public abstract void commitCapacity(Object stagedCapacity);

    public abstract void clearRange(int fromInclusive, int toExclusive);

    abstract long estimatedBytes(int capacity);

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
