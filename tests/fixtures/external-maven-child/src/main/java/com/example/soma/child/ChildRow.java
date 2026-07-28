package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "child_rows", defaultCapacity = 4)
public final class ChildRow {
    @SomaField public int value;
    public ChildRow() {}
}
