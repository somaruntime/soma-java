package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached primitive long aggregate aligned to first-key group order. */
public final class GroupedLongResult {
    private final String source;
    private final long structuralEpoch;
    private final int[] representativeIndexes;
    private final long[] values;

    GroupedLongResult(
            String source,
            long structuralEpoch,
            int[] representativeIndexes,
            long[] values) {
        this.source = source;
        this.structuralEpoch = structuralEpoch;
        this.representativeIndexes = representativeIndexes;
        this.values = values;
    }

    public String source() {
        return source;
    }

    public long structuralEpoch() {
        return structuralEpoch;
    }

    public int size() {
        return values.length;
    }

    public int representativeIndexAt(int group) {
        check(group);
        return representativeIndexes[group];
    }

    public long valueAt(int group) {
        check(group);
        return values[group];
    }

    public long[] values() {
        return Arrays.copyOf(values, values.length);
    }

    private void check(int group) {
        if (group < 0 || group >= values.length) {
            throw new IndexOutOfBoundsException("group ordinal out of range");
        }
    }
}
