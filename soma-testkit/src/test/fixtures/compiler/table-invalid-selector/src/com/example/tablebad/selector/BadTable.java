package com.example.tablebad.selector;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_optional", fields = {"optionalValue"})
public final class BadTable {
    @SomaField public int id;
    @SomaField @SomaOptional public Integer optionalValue;

    public BadTable() {
    }
}
