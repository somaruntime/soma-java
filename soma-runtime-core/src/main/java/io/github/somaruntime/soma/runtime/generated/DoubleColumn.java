package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

public final class DoubleColumn extends GeneratedColumn {
    private double[] head = new double[0];
    private double[][] tails = new double[0][];

    public DoubleColumn() {
    }

    public double get(int rowIndex) {
        return !segmented() || rowIndex < flatHeadRows()
                ? head[rowIndex]
                : tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)];
    }

    public void set(int rowIndex, double value) {
        if (!segmented() || rowIndex < flatHeadRows()) head[rowIndex] = value;
        else tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)] = value;
    }

    public void copyFrom(
            DoubleColumn source, int sourceIndex, int targetIndex, int length) {
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
        double[] stagedHead = newHead == head.length
                ? head : Arrays.copyOf(head, newHead);
        int newTails = tailCountFor(newCapacity);
        double[][] stagedTails = tails;
        if (newTails != tails.length) {
            stagedTails = Arrays.copyOf(tails, newTails);
            for (int ordinal = tails.length; ordinal < newTails; ordinal++) {
                stagedTails[ordinal] = new double[segmentRows()];
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
            Arrays.fill(head, from, to, 0.0d);
            return;
        }
        int cursor = from;
        if (cursor < head.length) {
            int end = Math.min(to, head.length);
            Arrays.fill(head, cursor, end, 0.0d);
            cursor = end;
        }
        while (cursor < to) {
            int ordinal = tailOrdinal(cursor);
            int offset = tailOffset(cursor);
            int end = Math.min(segmentRows(), offset + to - cursor);
            Arrays.fill(tails[ordinal], offset, end, 0.0d);
            cursor += end - offset;
        }
    }

    @Override long estimatedBytes(int capacity) {
        return ColumnStorageSupport.retainedBytes(this, capacity, 8);
    }
    @Override long stagingAllocationBytes(int capacity) {
        return ColumnStorageSupport.stagingAllocationBytes(
                this, head.length, tails.length, capacity, 8);
    }
    @Override long replacementTransientBytes(int capacity) {
        return ColumnStorageSupport.replacementTransientBytes(
                this, head.length, tails.length, capacity, 8);
    }
    @Override long retainedBytes() { return estimatedBytes(capacity()); }
    @Override void releaseStorage() {
        head = new double[0];
        tails = new double[0][];
    }

    private int capacity() { return capacityOf(head.length, tails.length); }

    private static final class Stage {
        final double[] head;
        final double[][] tails;
        Stage(double[] head, double[][] tails) {
            this.head = head;
            this.tails = tails;
        }
        int capacity() {
            return head.length + (tails.length == 0
                    ? 0 : Math.multiplyExact(tails.length, tails[0].length));
        }
    }
}
