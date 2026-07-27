package com.example.tablebad.object;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class BadObjectTable {
    @SomaField public int id;
    @SomaField public ApplicationObject applicationObject;

    public BadObjectTable() {
    }
}
