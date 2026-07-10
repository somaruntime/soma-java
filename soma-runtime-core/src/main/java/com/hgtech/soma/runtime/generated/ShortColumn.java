package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class ShortColumn extends GeneratedColumn {

    private short[] values = new short[0];

    public ShortColumn() {
    }

    public short get(int rowIndex) {
        return values[rowIndex];
    }

    public void set(int rowIndex, short value) {
        values[rowIndex] = value;
    }

    public void copyFrom(ShortColumn source, int sourceIndex, int targetIndex, int length) {
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
        if (!(stagedCapacity instanceof short[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        short[] staged = (short[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, (short) 0);
    }
}
