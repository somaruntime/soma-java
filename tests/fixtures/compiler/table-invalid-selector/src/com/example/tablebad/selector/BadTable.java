package com.example.tablebad.selector;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_optional", fields = {"optionalValue"})
public final class BadTable {
    @SomaField public int id;
    @SomaField @SomaOptional public Integer optionalValue;

    public BadTable() {
    }
}
