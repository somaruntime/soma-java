package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** Bounded primitive-value buffer used by specialized primitive pipelines. */
final class LongValueBuffer {

    private final long[] values;
    private int size;

    LongValueBuffer(long upperBound, Object provenance) {
        this(upperBound, SomaOperation.QUERY, provenance);
    }

    LongValueBuffer(
            long upperBound,
            SomaOperation operation,
            Object provenance) {
        values = new long[RowExecutionSupport.arrayLength(
                upperBound, operation, provenance)];
    }

    void add(long value) {
        if (size >= values.length) throw new AssertionError("value upper bound drift");
        values[size++] = value;
    }

    long get(int index) {
        return values[index];
    }

    void set(int index, long value) {
        values[index] = value;
    }

    int size() {
        return size;
    }

    void size(int next) {
        if (next < 0 || next > size) throw new AssertionError("invalid value buffer size");
        size = next;
    }

    long[] backing() {
        return values;
    }
}
