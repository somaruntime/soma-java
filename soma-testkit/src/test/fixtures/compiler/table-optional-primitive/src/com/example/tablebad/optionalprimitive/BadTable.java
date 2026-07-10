package com.example.tablebad.optionalprimitive;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class BadTable {
    @SomaField
    @SomaOptional
    public int value;

    public BadTable() {
    }
}
