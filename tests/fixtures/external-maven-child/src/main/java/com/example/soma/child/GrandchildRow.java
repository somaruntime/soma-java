package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "grandchild_rows", defaultCapacity = 2)
public final class GrandchildRow {
    @SomaField public long amount;
    public GrandchildRow() {}
}
