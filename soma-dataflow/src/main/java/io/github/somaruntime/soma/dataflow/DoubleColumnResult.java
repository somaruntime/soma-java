package io.github.somaruntime.soma.dataflow;

import java.util.Arrays;

/** Detached heap-resident primitive double column. */
public final class DoubleColumnResult {
    private final double[] values;
    private final int size;

    DoubleColumnResult(double[] values, int size) {
        this.values = values;
        this.size = size;
    }

    public int size() {
        return size;
    }

    public double valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
        return values[index];
    }

    public double[] toArray() {
        return Arrays.copyOf(values, size);
    }
}
