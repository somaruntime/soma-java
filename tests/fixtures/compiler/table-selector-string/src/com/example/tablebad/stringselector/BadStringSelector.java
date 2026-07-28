package com.example.tablebad.stringselector;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable
@SomaIndex(name = "by_label", fields = {"label.value"})
public final class BadStringSelector {
    @SomaField public int id;
    @SomaField public Label label;

    public BadStringSelector() {
    }
}
