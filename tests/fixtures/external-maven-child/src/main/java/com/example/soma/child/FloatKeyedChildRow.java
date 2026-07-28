package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "float_keyed_child_rows")
public final class FloatKeyedChildRow {
    @SomaKey public float id;
    @SomaField public int value;
    public FloatKeyedChildRow() {}
}
