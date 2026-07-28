package com.example.tablebad.child;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class BadParent {
    @SomaField public int id;
    @SomaChild(initialCapacity = 0) public List<KeyedRow> wrongList;
    public BadParent() {}
}
