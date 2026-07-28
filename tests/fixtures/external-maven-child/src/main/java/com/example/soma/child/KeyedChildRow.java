package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

import java.util.List;

@SomaTable(name = "keyed_child_rows", defaultCapacity = 4)
public final class KeyedChildRow {
    @SomaKey public int id;
    @SomaField public int value;
    @SomaChild public List<GrandchildRow> grandchildren;
    public KeyedChildRow() {}
}
