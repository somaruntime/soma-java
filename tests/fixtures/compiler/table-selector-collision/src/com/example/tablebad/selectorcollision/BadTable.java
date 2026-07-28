package com.example.tablebad.selectorcollision;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_a_b", fields = {"a"})
@SomaIndex(name = "by_aB", fields = {"b"})
public final class BadTable {
    @SomaField public int a;
    @SomaField public int b;

    public BadTable() {
    }
}
