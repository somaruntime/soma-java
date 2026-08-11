package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Bounded primitive Table-locator buffer used only after managed-memory admission. */
final class IntLocatorBuffer {

    private final int[] values;
    private int size;

    IntLocatorBuffer(long upperBound, Object provenance) {
        this(upperBound, SomaOperation.QUERY, provenance);
    }

    IntLocatorBuffer(
            long upperBound,
            SomaOperation operation,
            Object provenance) {
        values = new int[RowExecutionSupport.arrayLength(
                upperBound, operation, provenance)];
    }

    void add(int value) {
        if (value < 0 || size >= values.length) {
            throw new AssertionError("locator upper bound drift");
        }
        values[size++] = value;
    }

    int get(int index) {
        return values[index];
    }

    void set(int index, int value) {
        if (value < 0) throw new AssertionError("negative Table locator");
        values[index] = value;
    }

    int size() {
        return size;
    }

    void size(int next) {
        if (next < 0 || next > size) throw new AssertionError("invalid locator buffer size");
        size = next;
    }

    int[] backing() {
        return values;
    }
}
