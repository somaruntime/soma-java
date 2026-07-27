package com.example.soma.groupother;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
public final class OtherRow {
    @SomaKey
    public long id;

    @SomaField
    public int value;

    public OtherRow() {
    }
}
