package com.hgtech.soma.dataflow;

/** Required boolean scalar result. */
public final class BooleanScalarResult {
    private final boolean value;

    public BooleanScalarResult(boolean value) {
        this.value = value;
    }

    public boolean value() {
        return value;
    }
}
