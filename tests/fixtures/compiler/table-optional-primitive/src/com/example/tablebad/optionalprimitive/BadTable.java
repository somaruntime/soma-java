package com.example.tablebad.optionalprimitive;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class BadTable {
    @SomaField
    @SomaOptional
    public int value;

    public BadTable() {
    }
}
