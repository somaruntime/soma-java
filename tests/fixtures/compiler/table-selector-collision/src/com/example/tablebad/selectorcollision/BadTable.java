package com.example.tablebad.selectorcollision;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_a_b", fields = {"a"})
@SomaIndex(name = "by_aB", fields = {"b"})
public final class BadTable {
    @SomaField public int a;
    @SomaField public int b;

    public BadTable() {
    }
}
