package com.example.soma.breadth;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;

@SomaTable
@SomaIndex(name = "by_label", fields = {"label"})
@SomaUnique(name = "unique_label", fields = {"label"})
public final class StringSelectorRow {
    @SomaField public int id;
    @SomaField public String label;

    public StringSelectorRow() {
    }
}
