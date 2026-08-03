package io.github.somaruntime.soma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.somaruntime.soma.internal.SomaRuntimeAccess;

/** Detached, encounter-ordered result for the bounded I5 integer GroupBy slice. */
public final class IntGroupedLongResult {
    private final int[] keys;
    private final long[] values;

    private IntGroupedLongResult(int[] keys, long[] values) {
        this.keys = keys;
        this.values = values;
    }

    public long size() {
        return keys.length;
    }

    public void forEach(SomaIntLongConsumer consumer) {
        if (consumer == null) {
            throw SomaRuntimeAccess.failure(
                    SomaFailureCode.INVALID_ARGUMENT, SomaOperation.QUERY,
                    "consumer must not be null", null);
        }
        try {
            for (int i = 0; i < keys.length; i++) {
                consumer.accept(keys[i], values[i]);
            }
        } catch (RuntimeException failure) {
            throw SomaRuntimeAccess.failure(
                    SomaFailureCode.CALLBACK_FAILED, SomaOperation.QUERY,
                    "GroupBy consumer failed", failure);
        }
    }

    public List<IntGroupedLongEntry> toList() {
        List<IntGroupedLongEntry> result = new ArrayList<IntGroupedLongEntry>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            result.add(IntGroupedLongEntry.trustedCreate(keys[i], values[i]));
        }
        return result;
    }

    public IntGroupedLongEntry[] toArray() {
        IntGroupedLongEntry[] result = new IntGroupedLongEntry[keys.length];
        for (int i = 0; i < keys.length; i++) {
            result[i] = IntGroupedLongEntry.trustedCreate(keys[i], values[i]);
        }
        return result;
    }

    static IntGroupedLongResult trustedCreate(int[] keys, long[] values) {
        if (keys == null || values == null || keys.length != values.length) {
            throw new IllegalArgumentException("group result arrays must have equal length");
        }
        return new IntGroupedLongResult(
                Arrays.copyOf(keys, keys.length), Arrays.copyOf(values, values.length));
    }
}
