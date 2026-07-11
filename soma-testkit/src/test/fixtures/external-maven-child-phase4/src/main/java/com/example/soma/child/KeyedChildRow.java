package com.example.soma.child;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

import java.util.List;

@SomaTable(name = "keyed_child_rows", defaultCapacity = 4)
public final class KeyedChildRow {
    @SomaKey public int id;
    @SomaField public int value;
    @SomaChild public List<GrandchildRow> grandchildren;
    public KeyedChildRow() {}
}
