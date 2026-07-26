package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached heap-resident primitive long column. */
public final class LongColumnResult {
    private final long[] values;
    private final int size;

    LongColumnResult(long[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public long valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
        return values[index];
    }

    public long[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
