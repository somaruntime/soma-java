package com.example.tablebad.generatedcollision;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class BadTable {
    @SomaField
    @SomaOptional
    public Integer value;

    @SomaField
    public boolean valuePresent;

    public BadTable() {
    }
}
