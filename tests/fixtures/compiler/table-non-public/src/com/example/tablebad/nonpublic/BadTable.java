package com.example.tablebad.nonpublic;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
final class BadTable {
    @SomaField
    public int value;

    public BadTable() {
    }
}
