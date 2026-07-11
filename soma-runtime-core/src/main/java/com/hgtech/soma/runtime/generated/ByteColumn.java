package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class ByteColumn extends GeneratedColumn {

    private byte[] values = new byte[0];

    public ByteColumn() {
    }

    public byte get(int rowIndex) {
        return values[rowIndex];
    }

    public void set(int rowIndex, byte value) {
        values[rowIndex] = value;
    }

    public void copyFrom(ByteColumn source, int sourceIndex, int targetIndex, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        requireCopyRange(sourceIndex, targetIndex, length, source.values.length, values.length);
        System.arraycopy(source.values, sourceIndex, values, targetIndex, length);
    }

    @Override
    public Object stageCapacity(int newCapacity) {
        requireGrowth(values.length, newCapacity);
        return Arrays.copyOf(values, newCapacity);
    }

    @Override
    public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof byte[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        byte[] staged = (byte[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, (byte) 0);
    }

    @Override long estimatedBytes(int capacity) { requireCapacity(capacity); return capacity; }
    @Override long retainedBytes() { return values.length; }
    @Override void releaseStorage() { values = new byte[0]; }
}
