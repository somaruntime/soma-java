package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

public final class BooleanColumn extends GeneratedColumn {
    private boolean[] head = new boolean[0];
    private boolean[][] tails = new boolean[0][];

    public BooleanColumn() {
    }

    public boolean get(int rowIndex) {
        return !segmented() || rowIndex < flatHeadRows()
                ? head[rowIndex]
                : tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)];
    }

    public void set(int rowIndex, boolean value) {
        if (!segmented() || rowIndex < flatHeadRows()) head[rowIndex] = value;
        else tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)] = value;
    }

    public void copyFrom(
            BooleanColumn source, int sourceIndex, int targetIndex, int length) {
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
        int newHead = headCapacityFor(newCapacity);
        boolean[] stagedHead = newHead == head.length
                ? head : Arrays.copyOf(head, newHead);
        int newTails = tailCountFor(newCapacity);
        boolean[][] stagedTails = tails;
        if (newTails != tails.length) {
            stagedTails = Arrays.copyOf(tails, newTails);
            for (int ordinal = tails.length; ordinal < newTails; ordinal++) {
                stagedTails[ordinal] = new boolean[segmentRows()];
            }
        }
        return new Stage(stagedHead, stagedTails);
    }

    @Override public void commitCapacity(Object value) {
        if (!(value instanceof Stage)) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        Stage staged = (Stage) value;
        requireGrowth(capacity(), staged.capacity());
        head = staged.head;
        tails = staged.tails;
    }

    @Override public void clearRange(int from, int to) {
        requireRange(from, to, capacity());
        if (!segmented()) {
            Arrays.fill(head, from, to, false);
            return;
        }
        int cursor = from;
        if (cursor < head.length) {
            int end = Math.min(to, head.length);
            Arrays.fill(head, cursor, end, false);
            cursor = end;
        }
        while (cursor < to) {
            int ordinal = tailOrdinal(cursor);
            int offset = tailOffset(cursor);
            int end = Math.min(segmentRows(), offset + to - cursor);
            Arrays.fill(tails[ordinal], offset, end, false);
            cursor += end - offset;
        }
    }

    @Override long estimatedBytes(int capacity) {
        return ColumnStorageSupport.retainedBytes(this, capacity, 1);
    }
    @Override long stagingAllocationBytes(int capacity) {
        return ColumnStorageSupport.stagingAllocationBytes(
                this, head.length, tails.length, capacity, 1);
    }
    @Override long replacementTransientBytes(int capacity) {
        return ColumnStorageSupport.replacementTransientBytes(
                this, head.length, tails.length, capacity, 1);
    }
    @Override long retainedBytes() { return estimatedBytes(capacity()); }
    @Override void releaseStorage() {
        head = new boolean[0];
        tails = new boolean[0][];
    }

    private int capacity() { return capacityOf(head.length, tails.length); }

    private static final class Stage {
        final boolean[] head;
        final boolean[][] tails;
        Stage(boolean[] head, boolean[][] tails) {
            this.head = head;
            this.tails = tails;
        }
        int capacity() {
            return head.length + (tails.length == 0
                    ? 0 : Math.multiplyExact(tails.length, tails[0].length));
        }
    }
}
