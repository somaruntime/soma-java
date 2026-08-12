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

    /** Adds {@code value * repetitions} exactly without expanding an RLE run. */
    void addRepeated(long value, int repetitions) {
        if (repetitions < 0) throw new AssertionError("negative repetition count");
        long multiplier = repetitions;
        long addHigh = value < 0L ? -1L : 0L;
        long addLow = value;
        while (multiplier != 0L) {
            if ((multiplier & 1L) != 0L) add128(addHigh, addLow);
            multiplier >>>= 1;
            if (multiplier != 0L) {
                addHigh = (addHigh << 1) | (addLow >>> 63);
                addLow <<= 1;
            }
        }
    }

    private void add128(long valueHigh, long valueLow) {
        long before = low;
        low += valueLow;
        high += valueHigh;
        if (Long.compareUnsigned(low, before) < 0) high++;
    }

    /** Exact associative merge used by deterministic partial aggregates. */
    void add(Signed128Accumulator partial) {
        if (partial == null) throw new AssertionError("partial sum is missing");
        long before = low;
        low += partial.low;
        high += partial.high;
        if (Long.compareUnsigned(low, before) < 0) high++;
    }

    void addBytes(byte[] values, int length) {
        long nextHigh = high;
        long nextLow = low;
        for (int index = 0; index < length; index++) {
            long value = values[index];
            long before = nextLow;
            nextLow += value;
            nextHigh += value < 0L ? -1L : 0L;
            if (Long.compareUnsigned(nextLow, before) < 0) nextHigh++;
        }
        high = nextHigh;
        low = nextLow;
    }

    void addShorts(short[] values, int length) {
        long nextHigh = high;
        long nextLow = low;
        for (int index = 0; index < length; index++) {
            long value = values[index];
            long before = nextLow;
            nextLow += value;
            nextHigh += value < 0L ? -1L : 0L;
            if (Long.compareUnsigned(nextLow, before) < 0) nextHigh++;
        }
        high = nextHigh;
        low = nextLow;
    }

    void addChars(char[] values, int length) {
        long nextHigh = high;
        long nextLow = low;
        for (int index = 0; index < length; index++) {
            long before = nextLow;
            nextLow += values[index];
            if (Long.compareUnsigned(nextLow, before) < 0) nextHigh++;
        }
        high = nextHigh;
        low = nextLow;
    }

    void addInts(int[] values, int length) {
        long nextHigh = high;
        long nextLow = low;
        for (int index = 0; index < length; index++) {
            long value = values[index];
            long before = nextLow;
            nextLow += value;
            nextHigh += value < 0L ? -1L : 0L;
            if (Long.compareUnsigned(nextLow, before) < 0) nextHigh++;
        }
        high = nextHigh;
        low = nextLow;
    }

    void addLongs(long[] values, int length) {
        long nextHigh = high;
        long nextLow = low;
        for (int index = 0; index < length; index++) {
            long value = values[index];
            long before = nextLow;
            nextLow += value;
            nextHigh += value < 0L ? -1L : 0L;
            if (Long.compareUnsigned(nextLow, before) < 0) nextHigh++;
        }
        high = nextHigh;
        low = nextLow;
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
