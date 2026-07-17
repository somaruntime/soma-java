package com.example.soma.access;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable(name = "AccessRecord", defaultCapacity = 4)
@SomaIndex(name = "by_state", fields = {"state"})
@SomaIndex(name = "by_group", fields = {"group"})
@SomaUnique(name = "by_code", fields = {"code"})
public final class AccessRecord {
    @SomaField public int code;
    @SomaField public int state;
    @SomaField public int group;
    @SomaField public int score;

    public AccessRecord() {
    }
}
