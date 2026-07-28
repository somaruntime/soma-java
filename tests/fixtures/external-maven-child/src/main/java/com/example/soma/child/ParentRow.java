package com.example.soma.child;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

import java.util.List;
import java.util.Map;

@SomaTable(name = "parent_rows", defaultCapacity = 4)
public final class ParentRow {
    public static Runnable constructionHook;
    @SomaField public int id;
    @SomaChild(initialCapacity = 2) public List<ChildRow> children;
    @SomaChild @SomaOptional public List<ChildRow> optionalChildren;
    @SomaChild(initialCapacity = 3) public Map<Integer, KeyedChildRow> keyedChildren;
    public ParentRow() {
        Runnable hook = constructionHook;
        if (hook != null) hook.run();
    }
}
