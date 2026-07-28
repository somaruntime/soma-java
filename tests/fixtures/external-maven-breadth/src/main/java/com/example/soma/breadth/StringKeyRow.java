package com.example.soma.breadth;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "string_key_rows")
public final class StringKeyRow {
    @SomaKey public String id;
    @SomaField public int value;
    @SomaField @SomaOptional public String note;
}
