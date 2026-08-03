package com.example.soma.i2;

import io.github.somaruntime.soma.SomaFailureCode;
import io.github.somaruntime.soma.SomaOperation;
import io.github.somaruntime.soma.SomaOperationException;
import io.github.somaruntime.soma.internal.PrimitiveLongTableRuntime;
import io.github.somaruntime.soma.internal.ScalarTableRuntime;

/** Direct internal regression probe for I2 StateRoot and checked-arithmetic contracts. */
public final class RuntimeContractProbe {
    private RuntimeContractProbe() { }

    public static void main(String[] args) {
        System.setProperty("soma.test.chunkSize", "2");
        PrimitiveLongTableRuntime.GroupRuntime group = new PrimitiveLongTableRuntime.GroupRuntime();
        ScalarTableRuntime.FieldSpec[] specs = new ScalarTableRuntime.FieldSpec[] {
            new ScalarTableRuntime.FieldSpec(ScalarTableRuntime.FieldKind.LONG, true, false),
            new ScalarTableRuntime.FieldSpec(ScalarTableRuntime.FieldKind.REFERENCE, false, false, true)
        };
        ScalarTableRuntime runtime = new ScalarTableRuntime(group, 1L, specs, 0);
        ScalarTableRuntime.Append append = runtime.beginAppend();
        append.setLong(0, 1L).setReference(1, "stable").commit();
        long before = runtime.stateVersion();
        ScalarTableRuntime.PointUpdate update = runtime.beginUpdate(Long.valueOf(1L));
        update.setReference(1, new String("stable"));
        update.prepare();
        if (update.changed()) throw new AssertionError("content-equal String update changed");
        update.commit();
        if (runtime.stateVersion() != before) throw new AssertionError("no-op update published a root");
        long beforeRemove = runtime.stateVersion();
        if (runtime.remove(Long.valueOf(1L)).removed() != 1L
                || runtime.size() != 0L
                || runtime.stateVersion() != beforeRemove + 1L) {
            throw new AssertionError("point remove");
        }
        if (runtime.remove(Long.valueOf(1L)).removed() != 0L
                || runtime.stateVersion() != beforeRemove + 1L) {
            throw new AssertionError("missing remove");
        }
        ScalarTableRuntime packed = new ScalarTableRuntime(group, 2L, specs, 0);
        for (long key = 10L; key <= 14L; key++) {
            ScalarTableRuntime.Append row = packed.beginAppend();
            row.setLong(0, key).setReference(1, "value-" + key).commit();
        }
        long packedCapacity = packed.capacity();
        long packedVersion = packed.stateVersion();
        if (packed.remove(Long.valueOf(12L)).removed() != 1L
                || packed.capacity() != packedCapacity
                || packed.stateVersion() != packedVersion + 1L) {
            throw new AssertionError("middle compaction");
        }
        ScalarTableRuntime.Query packedQuery = packed.beginQuery(SomaOperation.QUERY);
        try {
            long[] expected = new long[] {10L, 11L, 13L, 14L};
            for (int i = 0; i < expected.length; i++) {
                if (packedQuery.findLocator(Long.valueOf(expected[i])) != i
                        || !("value-" + expected[i]).equals(packedQuery.referenceAt(1, i))) {
                    throw new AssertionError("survivor order");
                }
            }
        } finally {
            packedQuery.close();
        }
        ScalarTableRuntime referenceKey = new ScalarTableRuntime(group, 1L,
                new ScalarTableRuntime.FieldSpec[] {
                    new ScalarTableRuntime.FieldSpec(ScalarTableRuntime.FieldKind.REFERENCE, true, false, true)
                }, 0);
        ScalarTableRuntime.Append referenceRow = referenceKey.beginAppend();
        referenceRow.setReference(0, "key").commit();
        try {
            referenceKey.remove(null);
            throw new AssertionError("null key accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.INVALID_ARGUMENT
                    || failure.operation() != SomaOperation.REMOVE) {
                throw new AssertionError("unstable null-key failure", failure);
            }
        }
        try {
            runtime.reserve(Long.MAX_VALUE);
            throw new AssertionError("overflow reserve accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.ARITHMETIC_OVERFLOW
                    || failure.operation() != SomaOperation.RESERVE) {
                throw new AssertionError("unstable overflow failure: " + failure.code(), failure);
            }
        }
        ScalarTableRuntime.CheckedLongAccumulator cancellation =
                new ScalarTableRuntime.CheckedLongAccumulator();
        cancellation.add(Long.MAX_VALUE);
        cancellation.add(1L);
        cancellation.add(-1L);
        if (!cancellation.fitsLong() || cancellation.value() != Long.MAX_VALUE) {
            throw new AssertionError("two-limb cancellation");
        }
        ScalarTableRuntime.CheckedLongAccumulator overflow =
                new ScalarTableRuntime.CheckedLongAccumulator();
        overflow.add(Long.MAX_VALUE);
        overflow.add(1L);
        if (overflow.fitsLong()) throw new AssertionError("two-limb overflow accepted");
    }
}
