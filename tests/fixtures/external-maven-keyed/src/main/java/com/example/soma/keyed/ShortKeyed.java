package com.example.soma.keyed;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class ShortKeyed {
    @SomaKey public short id;
    @SomaField public int payload;
    public ShortKeyed() {
    }
}
