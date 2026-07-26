package com.hgtech.soma.dataflow;

/** Explicitly present or absent floating scalar result. */
public final class OptionalDoubleResult {
    private static final OptionalDoubleResult EMPTY =
            new OptionalDoubleResult(false, 0.0d);

    private final boolean present;
    private final double value;

    private OptionalDoubleResult(boolean present, double value) {
        this.present = present;
        this.value = value;
    }

    public static OptionalDoubleResult empty() {
        return EMPTY;
    }

    public static OptionalDoubleResult of(double value) {
        return new OptionalDoubleResult(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public double value() {
        if (!present) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_empty_scalar",
                    "scalar",
                    "scalar.value");
        }
        return value;
    }
}
