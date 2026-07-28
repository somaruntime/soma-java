package com.example.tablebad.child;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable public final class BadParent {
    @SomaField public int id;
    @SomaChild(initialCapacity = 0) public List<KeyedRow> wrongList;
    public BadParent() {}
}
