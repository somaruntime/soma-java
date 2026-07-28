package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_id", fields = {"id"})
public final class MutatorAtomicAccess {
    public static boolean failConstruction;

    @SomaField public int id;
    @SomaField public AccessState state;

    public MutatorAtomicAccess() {
        if (failConstruction) throw new IllegalStateException("carrier construction failed");
    }
}
