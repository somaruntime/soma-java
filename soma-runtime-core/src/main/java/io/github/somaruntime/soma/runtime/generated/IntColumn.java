package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

public final class IntColumn extends GeneratedColumn {
    private int[] head = new int[0];
    private int[][] tails = new int[0][];

    public IntColumn() {
    }

    public int get(int rowIndex) {
        return !segmented() || rowIndex < flatHeadRows()
                ? head[rowIndex]
                : tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)];
    }

    public void set(int rowIndex, int value) {
        if (!segmented() || rowIndex < flatHeadRows()) {
            head[rowIndex] = value;
        } else {
            tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)] = value;
        }
    }

    public void copyFrom(
            IntColumn source, int sourceIndex, int targetIndex, int length) {
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

    @Override
    public Object stageCapacity(int newCapacity) {
        requireGrowth(capacity(), newCapacity);
        int newHeadCapacity = headCapacityFor(newCapacity);
        int[] stagedHead = newHeadCapacity == head.length
                ? head : Arrays.copyOf(head, newHeadCapacity);
        int newTailCount = tailCountFor(newCapacity);
        int[][] stagedTails = tails;
        if (newTailCount != tails.length) {
            stagedTails = Arrays.copyOf(tails, newTailCount);
            for (int ordinal = tails.length; ordinal < newTailCount; ordinal++) {
                stagedTails[ordinal] = new int[segmentRows()];
            }
        }
        return new Stage(stagedHead, stagedTails);
    }

    @Override
    public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof Stage)) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        Stage staged = (Stage) stagedCapacity;
        requireGrowth(capacity(), staged.capacity());
        head = staged.head;
        tails = staged.tails;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, capacity());
        if (!segmented()) {
            Arrays.fill(head, fromInclusive, toExclusive, 0);
            return;
        }
        int cursor = fromInclusive;
        if (cursor < head.length) {
            int end = Math.min(toExclusive, head.length);
            Arrays.fill(head, cursor, end, 0);
            cursor = end;
        }
        while (cursor < toExclusive) {
            int ordinal = tailOrdinal(cursor);
            int offset = tailOffset(cursor);
            int end = Math.min(
                    segmentRows(), offset + toExclusive - cursor);
            Arrays.fill(tails[ordinal], offset, end, 0);
            cursor += end - offset;
        }
    }

    @Override long estimatedBytes(int capacity) {
        return ColumnStorageSupport.retainedBytes(this, capacity, 4);
    }

    @Override long stagingAllocationBytes(int newCapacity) {
        return ColumnStorageSupport.stagingAllocationBytes(
                this, head.length, tails.length, newCapacity, 4);
    }

    @Override long replacementTransientBytes(int newCapacity) {
        return ColumnStorageSupport.replacementTransientBytes(
                this, head.length, tails.length, newCapacity, 4);
    }

    @Override long retainedBytes() {
        return estimatedBytes(capacity());
    }

    @Override void releaseStorage() {
        head = new int[0];
        tails = new int[0][];
    }

    private int capacity() {
        return capacityOf(head.length, tails.length);
    }

    private static final class Stage {
        private final int[] head;
        private final int[][] tails;

        private Stage(int[] head, int[][] tails) {
            this.head = head;
            this.tails = tails;
        }

        private int capacity() {
            return head.length
                    + (tails.length == 0
                    ? 0 : Math.multiplyExact(tails.length, tails[0].length));
        }
    }
}
