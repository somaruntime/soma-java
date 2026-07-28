package com.example.tablebad.capacity;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(defaultCapacity = 0)
public final class BadTable {
    @SomaField
    public int value;

    public BadTable() {
    }
}
