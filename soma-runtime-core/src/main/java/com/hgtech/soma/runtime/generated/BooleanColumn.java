package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class BooleanColumn extends GeneratedColumn {

    private boolean[] values = new boolean[0];

    public BooleanColumn() {
    }

    public boolean get(int rowIndex) {
        return values[rowIndex];
    }

    public void set(int rowIndex, boolean value) {
        values[rowIndex] = value;
    }

    public void copyFrom(BooleanColumn source, int sourceIndex, int targetIndex, int length) {
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
        if (!(stagedCapacity instanceof boolean[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        boolean[] staged = (boolean[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, false);
    }
}
