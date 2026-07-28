package com.example.soma.child;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "grandchild_rows", defaultCapacity = 2)
public final class GrandchildRow {
    @SomaField public long amount;
    public GrandchildRow() {}
}
