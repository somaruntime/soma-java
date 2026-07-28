package com.example.soma.child;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

import java.util.Map;

@SomaTable(name = "floating_parent_rows")
public final class FloatingParentRow {
    @SomaField public int id;
    @SomaChild public Map<Float, FloatKeyedChildRow> floatChildren;
    @SomaChild public Map<Double, DoubleKeyedChildRow> doubleChildren;
    public FloatingParentRow() {}
}
