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
        System.setProperty("soma.test.chunkSize", "1048576");
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
        try {
            runtime.reserve(Long.MAX_VALUE);
            throw new AssertionError("overflow reserve accepted");
        } catch (SomaOperationException failure) {
            if (failure.code() != SomaFailureCode.ARITHMETIC_OVERFLOW
                    || failure.operation() != SomaOperation.RESERVE) {
                throw new AssertionError("unstable overflow failure: " + failure.code(), failure);
            }
        }
    }
}
