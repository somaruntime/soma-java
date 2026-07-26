package com.hgtech.soma.dataflow;

/** Required double scalar result. */
public final class DoubleScalarResult {
    private final double value;

    public DoubleScalarResult(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }
}
