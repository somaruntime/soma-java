package com.example.soma.floatingvalue;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "floating_rows")
public final class FloatingRow {
    @SomaField public FloatingPayload payload;
}
