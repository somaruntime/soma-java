package io.github.somaruntime.soma.dataflow;

/** Explicitly present or absent integral scalar result. */
public final class OptionalLongResult {
    private static final OptionalLongResult EMPTY =
            new OptionalLongResult(false, 0L);

    private final boolean present;
    private final long value;

    private OptionalLongResult(boolean present, long value) {
        this.present = present;
        this.value = value;
    }

    public static OptionalLongResult empty() {
        return EMPTY;
    }

    public static OptionalLongResult of(long value) {
        return new OptionalLongResult(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    public long value() {
        if (!present) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_empty_scalar",
                    "scalar",
                    "scalar.value");
        }
        return value;
    }
}
