package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;

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
