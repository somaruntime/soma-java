package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Exact signed 128-bit accumulator for a long-domain sequence. */
final class Signed128Accumulator {

    private long high;
    private long low;

    void add(long value) {
        long before = low;
        low += value;
        high += value < 0L ? -1L : 0L;
        if (Long.compareUnsigned(low, before) < 0) high++;
    }

    long longValue(Object provenance) {
        if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) {
            return low;
        }
        throw SomaFailures.failure(
                SomaFailureCode.ARITHMETIC_OVERFLOW,
                SomaOperation.QUERY,
                "integer aggregate exceeds signed long range",
                provenance);
    }

    double doubleValue() {
        double unsignedLow = (double) (low & Long.MAX_VALUE);
        if (low < 0L) unsignedLow += 0x1.0p63;
        return (double) high * 0x1.0p64 + unsignedLow;
    }
}
