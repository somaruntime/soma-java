package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;

/** Checked arithmetic for the 32-bit Table structural domain. */
final class CheckedStructural {

    private CheckedStructural() {
    }

    static int increment(int value, SomaOperation operation, Object provenance) {
        if (value == Integer.MAX_VALUE) throw limit(operation, provenance);
        return value + 1;
    }

    static int fromLong(long value, SomaOperation operation, Object provenance) {
        if (value < 0L || value > Integer.MAX_VALUE) throw limit(operation, provenance);
        return (int) value;
    }

    static int ceilChunks(int rows, int chunkRows) {
        if (rows == 0) return 0;
        return (int) (((long) rows + chunkRows - 1L) / chunkRows);
    }

    private static RuntimeException limit(SomaOperation operation, Object provenance) {
        return SomaFailures.failure(
                SomaFailureCode.RESOURCE_LIMIT_EXCEEDED,
                operation,
                "SOMA structural domain exceeds the 32-bit product limit",
                provenance);
    }
}
