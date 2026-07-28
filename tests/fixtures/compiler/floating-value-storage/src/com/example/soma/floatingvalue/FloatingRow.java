package com.example.soma.floatingvalue;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "floating_rows")
public final class FloatingRow {
    @SomaField public FloatingPayload payload;
}
