package io.github.somaruntime.soma.runtime.generated;

import java.util.Arrays;

public final class ByteColumn extends GeneratedColumn {
    private byte[] head = new byte[0];
    private byte[][] tails = new byte[0][];

    public ByteColumn() {
    }

    public byte get(int rowIndex) {
        return !segmented() || rowIndex < flatHeadRows()
                ? head[rowIndex]
                : tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)];
    }

    public void set(int rowIndex, byte value) {
        if (!segmented() || rowIndex < flatHeadRows()) head[rowIndex] = value;
        else tails[tailOrdinal(rowIndex)][tailOffset(rowIndex)] = value;
    }

    public void copyFrom(
            ByteColumn source, int sourceIndex, int targetIndex, int length) {
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
        byte[] stagedHead = newHead == head.length
                ? head : Arrays.copyOf(head, newHead);
        int newTails = tailCountFor(newCapacity);
        byte[][] stagedTails = tails;
        if (newTails != tails.length) {
            stagedTails = Arrays.copyOf(tails, newTails);
            for (int ordinal = tails.length; ordinal < newTails; ordinal++) {
                stagedTails[ordinal] = new byte[segmentRows()];
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
            Arrays.fill(head, from, to, (byte) 0);
            return;
        }
        int cursor = from;
        if (cursor < head.length) {
            int end = Math.min(to, head.length);
            Arrays.fill(head, cursor, end, (byte) 0);
            cursor = end;
        }
        while (cursor < to) {
            int ordinal = tailOrdinal(cursor);
            int offset = tailOffset(cursor);
            int end = Math.min(segmentRows(), offset + to - cursor);
            Arrays.fill(tails[ordinal], offset, end, (byte) 0);
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
        head = new byte[0];
        tails = new byte[0][];
    }

    private int capacity() { return capacityOf(head.length, tails.length); }

    private static final class Stage {
        final byte[] head;
        final byte[][] tails;
        Stage(byte[] head, byte[][] tails) {
            this.head = head;
            this.tails = tails;
        }
        int capacity() {
            return head.length + (tails.length == 0
                    ? 0 : Math.multiplyExact(tails.length, tails[0].length));
        }
    }
}
