package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/**
 * String 与 opaque runtime reference leaf 使用的 packed object column。
 *
 * <p>Generated binding 决定 exact leaf type；runtime hot loop 不通过本类解释 schema，
 * clear/remove/replacement 必须及时清除 dead reference。</p>
 */
public final class ObjectColumn<T> extends GeneratedColumn {

    private Object[] values = new Object[0];

    public ObjectColumn() {
    }

    @SuppressWarnings("unchecked")
    public T get(int rowIndex) {
        return (T) values[rowIndex];
    }

    public void set(int rowIndex, T value) {
        values[rowIndex] = value;
    }

    public void copyFrom(
            ObjectColumn<T> source, int sourceIndex, int targetIndex, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        requireCopyRange(sourceIndex, targetIndex, length,
                source.values.length, values.length);
        System.arraycopy(source.values, sourceIndex, values, targetIndex, length);
    }

    @Override
    public Object stageCapacity(int newCapacity) {
        requireGrowth(values.length, newCapacity);
        return Arrays.copyOf(values, newCapacity);
    }

    @Override
    public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof Object[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        Object[] staged = (Object[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, null);
    }
}
