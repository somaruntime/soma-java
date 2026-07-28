package com.example.tablebad.checkedconstructor;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

import java.io.IOException;

@SomaTable
public final class BadTable {
    @SomaField
    public int value;

    public BadTable() throws IOException {
    }
}
