package com.example.soma.groupother;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class OtherRow {
    @SomaKey
    public long id;

    @SomaField
    public int value;

    public OtherRow() {
    }
}
