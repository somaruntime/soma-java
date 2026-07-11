package com.example.tablebad.childshape;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable public final class KeyedRow {
    @SomaKey public int id;
    @SomaField public int value;
    public KeyedRow() {}
}
