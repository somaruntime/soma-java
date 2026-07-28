package com.example.tablebad.object;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class BadObjectTable {
    @SomaField public int id;
    @SomaField public ApplicationObject applicationObject;

    public BadObjectTable() {
    }
}
