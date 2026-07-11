package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

public final class FloatColumn extends GeneratedColumn {

    private float[] values = new float[0];

    public FloatColumn() {
    }

    public float get(int rowIndex) {
        return values[rowIndex];
    }

    public void set(int rowIndex, float value) {
        values[rowIndex] = value;
    }

    public void copyFrom(FloatColumn source, int sourceIndex, int targetIndex, int length) {
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
        if (!(stagedCapacity instanceof float[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        float[] staged = (float[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, 0.0f);
    }

    @Override long estimatedBytes(int capacity) { requireCapacity(capacity); return 4L * capacity; }
    @Override long retainedBytes() { return 4L * values.length; }
    @Override void releaseStorage() { values = new float[0]; }
}
