package io.github.somaruntime.soma.dataflow;

/** Required long scalar result, used by count and integral reductions. */
public final class LongScalarResult {
    private final long value;

    LongScalarResult(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }
}
