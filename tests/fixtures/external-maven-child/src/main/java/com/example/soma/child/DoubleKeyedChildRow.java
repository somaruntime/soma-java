package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "double_keyed_child_rows")
public final class DoubleKeyedChildRow {
    @SomaKey public double id;
    @SomaField public int value;
    public DoubleKeyedChildRow() {}
}
