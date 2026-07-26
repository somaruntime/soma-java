package com.hgtech.soma.dataflow;

import java.util.Arrays;

/** Detached long column with explicit per-element presence. */
public final class OptionalLongColumnResult {
    private final long[] values;
    private final boolean[] present;

    OptionalLongColumnResult(long[] values, boolean[] present) {
        this.values = values;
        this.present = present;
    }

    public int size() {
        return values.length;
    }

    public boolean isPresent(int index) {
        check(index);
        return present[index];
    }

    public long valueAt(int index) {
        check(index);
        if (!present[index]) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_absent_join_side",
                    Integer.toString(index),
                    "dataflow.result");
        }
        return values[index];
    }

    public long[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public boolean[] presence() {
        return Arrays.copyOf(present, present.length);
    }

    private void check(int index) {
        if (index < 0 || index >= values.length) {
            throw new IndexOutOfBoundsException("result index out of range");
        }
    }
}
