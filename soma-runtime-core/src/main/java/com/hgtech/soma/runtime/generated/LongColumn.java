package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class LongColumn extends GeneratedColumn {
    private long[] head = new long[0];
    private long[][] tails = new long[0][];

    public LongColumn() {
    }

    public long get(int rowIndex) {
        return !segmented() || rowIndex < flatHeadRows()
                ? head[rowIndex]
                : tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)];
    }

    public void set(int rowIndex, long value) {
        if (!segmented() || rowIndex < flatHeadRows()) {
            head[rowIndex] = value;
        } else {
            tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)] = value;
        }
    }

    public void copyFrom(
            LongColumn source, int sourceIndex, int targetIndex, int length) {
        if (source == null) throw new NullPointerException("source");
        requireCopyRange(
                sourceIndex, targetIndex, length,
                source.capacity(), capacity());
        if (source == this && targetIndex > sourceIndex
                && targetIndex < sourceIndex + length) {
            for (int index = length - 1; index >= 0; index--) {
                set(targetIndex + index, get(sourceIndex + index));
            }
        } else {
            for (int index = 0; index < length; index++) {
                set(targetIndex + index, source.get(sourceIndex + index));
            }
        }
    }

    @Override public Object stageCapacity(int newCapacity) {
        requireGrowth(capacity(), newCapacity);
        int newHeadCapacity = headCapacityFor(newCapacity);
        long[] stagedHead = newHeadCapacity == head.length
                ? head : Arrays.copyOf(head, newHeadCapacity);
        int newTailCount = tailCountFor(newCapacity);
        long[][] stagedTails = tails;
        if (newTailCount != tails.length) {
            stagedTails = Arrays.copyOf(tails, newTailCount);
            for (int ordinal = tails.length; ordinal < newTailCount; ordinal++) {
                stagedTails[ordinal] = new long[segmentRows()];
            }
        }
        return new Stage(stagedHead, stagedTails);
    }

    @Override public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof Stage)) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        Stage staged = (Stage) stagedCapacity;
        requireGrowth(capacity(), staged.capacity());
        head = staged.head;
        tails = staged.tails;
    }

    @Override public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, capacity());
        if (!segmented()) {
            Arrays.fill(head, fromInclusive, toExclusive, 0L);
            return;
        }
        int cursor = fromInclusive;
        if (cursor < head.length) {
            int end = Math.min(toExclusive, head.length);
            Arrays.fill(head, cursor, end, 0L);
            cursor = end;
        }
        while (cursor < toExclusive) {
            int ordinal = tailOrdinal(cursor);
            int offset = tailOffset(cursor);
            int end = Math.min(
                    segmentRows(), offset + toExclusive - cursor);
            Arrays.fill(tails[ordinal], offset, end, 0L);
            cursor += end - offset;
        }
    }

    @Override long estimatedBytes(int capacity) {
        return ColumnStorageSupport.retainedBytes(this, capacity, 8);
    }

    @Override long stagingAllocationBytes(int newCapacity) {
        return ColumnStorageSupport.stagingAllocationBytes(
                this, head.length, tails.length, newCapacity, 8);
    }

    @Override long replacementTransientBytes(int newCapacity) {
        return ColumnStorageSupport.replacementTransientBytes(
                this, head.length, tails.length, newCapacity, 8);
    }

    @Override long retainedBytes() { return estimatedBytes(capacity()); }

    @Override void releaseStorage() {
        head = new long[0];
        tails = new long[0][];
    }

    private int capacity() { return capacityOf(head.length, tails.length); }

    private static final class Stage {
        private final long[] head;
        private final long[][] tails;
        private Stage(long[] head, long[][] tails) {
            this.head = head;
            this.tails = tails;
        }
        private int capacity() {
            return head.length + (tails.length == 0
                    ? 0 : Math.multiplyExact(tails.length, tails[0].length));
        }
    }
}
