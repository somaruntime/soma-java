package com.example.soma.child;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "double_keyed_child_rows")
public final class DoubleKeyedChildRow {
    @SomaKey public double id;
    @SomaField public int value;
    public DoubleKeyedChildRow() {}
}
