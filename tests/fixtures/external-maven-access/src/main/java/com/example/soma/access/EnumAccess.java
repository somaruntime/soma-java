package com.example.soma.access;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_state", fields = {"state"})
public final class EnumAccess {
    @SomaField public int id;
    @SomaField public AccessState state;
    @SomaField public int rank;

    public EnumAccess() {
    }
}
