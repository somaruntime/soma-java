package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.runtime.SomaRuntimeException;

/**
 * Package-owned fail-closed integral arithmetic.
 *
 * <p>Per-value operations use the signed {@code long} domain. Built-in sums
 * use a signed 128-bit accumulator so cancellation and parallel merge remain
 * exact before a public {@code long} result is validated.</p>
 */
final class IntegralArithmetic {
    static final String OVERFLOW_CODE = "dataflow_integral_overflow";
    static final String DIVISION_BY_ZERO_CODE =
            "dataflow_integral_division_by_zero";

    private static final double TWO_TO_63 = 0x1.0p63;
    private static final double TWO_TO_64 = 0x1.0p64;

    private IntegralArithmetic() {
    }

    static long add(
            long left,
            long right,
            String path,
            String operation) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw overflow(path, operation);
        }
    }

    static long subtract(
            long left,
            long right,
            String path,
            String operation) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException overflow) {
            throw overflow(path, operation);
        }
    }

    static long multiply(
            long left,
            long right,
            String path,
            String operation) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException overflow) {
            throw overflow(path, operation);
        }
    }

    static long divide(
            long left,
            long right,
            String path,
            String operation) {
        if (right == 0L) {
            throw DataFlowFailures.invalidInput(
                    DIVISION_BY_ZERO_CODE, path, operation);
        }
        if (left == Long.MIN_VALUE && right == -1L) {
            throw overflow(path, operation);
        }
        return left / right;
    }

    static void addTo(
            long[] highs,
            long[] lows,
            int index,
            long value) {
        addTo(
                highs,
                lows,
                index,
                value < 0L ? -1L : 0L,
                value);
    }

    static void addTo(
            long[] highs,
            long[] lows,
            int index,
            long otherHigh,
            long otherLow) {
        long previousLow = lows[index];
        long nextLow = previousLow + otherLow;
        lows[index] = nextLow;
        highs[index] += otherHigh;
        if (Long.compareUnsigned(nextLow, previousLow) < 0) {
            highs[index]++;
        }
    }

    static long longValue(
            long high,
            long low,
            String path,
            String operation) {
        if ((high == 0L && low >= 0L)
                || (high == -1L && low < 0L)) {
            return low;
        }
        throw overflow(path, operation);
    }

    static double doubleValue(long high, long low) {
        if ((high == 0L && low >= 0L)
                || (high == -1L && low < 0L)) {
            return (double) low;
        }
        if (high < 0L) {
            long magnitudeLow = -low;
            long magnitudeHigh = ~high;
            if (magnitudeLow == 0L) {
                magnitudeHigh++;
            }
            return -unsignedDoubleValue(
                    magnitudeHigh, magnitudeLow);
        }
        return unsignedDoubleValue(high, low);
    }

    private static double unsignedDoubleValue(long high, long low) {
        double highValue = high >= 0L
                ? (double) high
                : (double) (high & Long.MAX_VALUE) + TWO_TO_63;
        double lowValue = low >= 0L
                ? (double) low
                : (double) (low & Long.MAX_VALUE) + TWO_TO_63;
        return highValue * TWO_TO_64 + lowValue;
    }

    private static SomaRuntimeException overflow(
            String path, String operation) {
        return DataFlowFailures.invalidInput(
                OVERFLOW_CODE, path, operation);
    }

    static final class ExactSum {
        private long high;
        private long low;

        void add(long value) {
            addBits(value < 0L ? -1L : 0L, value);
        }

        void add(long otherHigh, long otherLow) {
            addBits(otherHigh, otherLow);
        }

        void subtract(long value) {
            long valueHigh = value < 0L ? -1L : 0L;
            long negatedLow = -value;
            long negatedHigh = ~valueHigh;
            if (negatedLow == 0L) {
                negatedHigh++;
            }
            addBits(negatedHigh, negatedLow);
        }

        void reset() {
            high = 0L;
            low = 0L;
        }

        long highBits() {
            return high;
        }

        long lowBits() {
            return low;
        }

        long longValue(String path, String operation) {
            return IntegralArithmetic.longValue(
                    high, low, path, operation);
        }

        double doubleValue() {
            return IntegralArithmetic.doubleValue(high, low);
        }

        private void addBits(long otherHigh, long otherLow) {
            long previousLow = low;
            low += otherLow;
            high += otherHigh;
            if (Long.compareUnsigned(low, previousLow) < 0) {
                high++;
            }
        }
    }
}
