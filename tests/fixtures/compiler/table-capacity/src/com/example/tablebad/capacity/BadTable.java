package com.example.tablebad.capacity;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(defaultCapacity = 0)
public final class BadTable {
    @SomaField
    public int value;

    public BadTable() {
    }
}
