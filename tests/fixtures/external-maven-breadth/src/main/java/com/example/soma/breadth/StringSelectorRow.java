package com.example.soma.breadth;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_label", fields = {"label"})
@SomaUnique(name = "unique_label", fields = {"label"})
public final class StringSelectorRow {
    @SomaField public int id;
    @SomaField public String label;

    public StringSelectorRow() {
    }
}
