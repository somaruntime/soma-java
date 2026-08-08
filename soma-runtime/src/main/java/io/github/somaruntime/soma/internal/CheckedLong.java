package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

final class CheckedLong {

    private CheckedLong() {
    }

    static long add(long left, long right, SomaOperation operation, Object provenance) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            throw overflow(operation, provenance);
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            throw overflow(operation, provenance);
        }
        return left + right;
    }

    static long multiply(
            long left,
            long right,
            SomaOperation operation,
            Object provenance) {
        if (left < 0L || right < 0L) {
            throw new AssertionError("SOMA managed arithmetic must be non-negative");
        }
        if (left != 0L && right > Long.MAX_VALUE / left) {
            throw overflow(operation, provenance);
        }
        return left * right;
    }

    static long increment(long value, SomaOperation operation, Object provenance) {
        return add(value, 1L, operation, provenance);
    }

    static long subtract(
            long left,
            long right,
            SomaOperation operation,
            Object provenance) {
        if (left < 0L || right < 0L || right > left) {
            throw new AssertionError("SOMA managed subtraction is invalid");
        }
        return left - right;
    }

    private static RuntimeException overflow(
            SomaOperation operation,
            Object provenance) {
        return SomaFailures.failure(
                SomaFailureCode.ARITHMETIC_OVERFLOW,
                operation,
                "checked SOMA arithmetic overflow",
                provenance);
    }
}
