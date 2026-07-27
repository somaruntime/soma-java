package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached heap-resident String reference column. */
public final class StringColumnResult {
    private final String[] values;
    private final int size;

    StringColumnResult(String[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public String valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
        return values[index];
    }

    public String[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
