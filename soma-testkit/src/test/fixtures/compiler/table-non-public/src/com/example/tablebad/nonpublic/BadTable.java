package com.example.tablebad.nonpublic;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
final class BadTable {
    @SomaField
    public int value;

    public BadTable() {
    }
}
