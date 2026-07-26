package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached heap-resident primitive boolean column. */
public final class BooleanColumnResult {
    private final boolean[] values;
    private final int size;

    BooleanColumnResult(boolean[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public boolean valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
        return values[index];
    }

    public boolean[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
