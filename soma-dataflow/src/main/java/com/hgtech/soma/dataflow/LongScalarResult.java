package com.hgtech.soma.dataflow;

/** Required long scalar result, used by count and integral reductions. */
public final class LongScalarResult {
    private final long value;

    public LongScalarResult(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }
}
