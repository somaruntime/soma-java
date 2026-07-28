package com.example.tablebad.childshape;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable public final class KeyedRow {
    @SomaKey public int id;
    @SomaField public int value;
    public KeyedRow() {}
}
