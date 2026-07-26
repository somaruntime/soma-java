package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached heap-resident reference/value column. */
public final class ObjectColumnResult<T> {
    private final Object[] values;
    private final int size;

    ObjectColumnResult(Object[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    public T valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
        return (T) values[index];
    }

    public Object[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
