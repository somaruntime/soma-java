package com.example.soma.child;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "float_keyed_child_rows")
public final class FloatKeyedChildRow {
    @SomaKey public float id;
    @SomaField public int value;
    public FloatKeyedChildRow() {}
}
