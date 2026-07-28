package com.example.tablebad.stringselector;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_label", fields = {"label.value"})
public final class BadStringSelector {
    @SomaField public int id;
    @SomaField public Label label;

    public BadStringSelector() {
    }
}
