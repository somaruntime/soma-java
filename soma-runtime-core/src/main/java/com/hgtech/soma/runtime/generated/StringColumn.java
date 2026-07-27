package com.hgtech.soma.runtime.generated;

import java.util.Arrays;

/**
 * V1 白名单 String reference-backed scalar 的 packed column。
 *
 * <p>本列只保存 caller String reference；不复制、intern、normalize、dictionary
 * encode 或读取 String 内部表示。Dead row 的 reference 必须在 mutation、
 * clear、rollback 与 release boundary 清理。</p>
 */
public final class StringColumn extends GeneratedColumn {

    private String[] values = new String[0];

    public StringColumn() {
    }

    public String get(int rowIndex) {
        return values[rowIndex];
    }

    public void set(int rowIndex, String value) {
        values[rowIndex] = value;
    }

    public void copyFrom(
            StringColumn source, int sourceIndex, int targetIndex, int length) {
        if (source == null) {
            throw new NullPointerException("source");
        }
        requireCopyRange(
                sourceIndex, targetIndex, length,
                source.values.length, values.length);
        System.arraycopy(
                source.values, sourceIndex, values, targetIndex, length);
    }

    @Override
    public Object stageCapacity(int newCapacity) {
        requireGrowth(values.length, newCapacity);
        return Arrays.copyOf(values, newCapacity);
    }

    @Override
    public void commitCapacity(Object stagedCapacity) {
        if (!(stagedCapacity instanceof String[])) {
            throw new IllegalStateException("staged capacity type mismatch");
        }
        String[] staged = (String[]) stagedCapacity;
        requireGrowth(values.length, staged.length);
        values = staged;
    }

    @Override
    public void clearRange(int fromInclusive, int toExclusive) {
        requireRange(fromInclusive, toExclusive, values.length);
        Arrays.fill(values, fromInclusive, toExclusive, null);
    }

    @Override
    long estimatedBytes(int capacity) {
        requireCapacity(capacity);
        return 8L * capacity;
    }

    @Override
    long retainedBytes() {
        return 8L * values.length;
    }

    @Override
    void releaseStorage() {
        values = new String[0];
    }
}
