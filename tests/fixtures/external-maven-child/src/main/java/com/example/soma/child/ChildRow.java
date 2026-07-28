package com.example.soma.child;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "child_rows", defaultCapacity = 4)
public final class ChildRow {
    @SomaField public int value;
    public ChildRow() {}
}
