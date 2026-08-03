package com.example.soma.i2.soma.schema;

import com.example.soma.i2.StateCode;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 2L)
final class ScalarRecord {
    @SomaKey long id;
    @SomaIndex int machine;
    @SomaIndex String label;
    @SomaField boolean enabled;
    @SomaField byte small;
    @SomaField short medium;
    @SomaField char marker;
    @SomaField int count;
    @SomaField float ratio;
    @SomaField double score;
    @SomaField String note;
    @SomaField StateCode state;
}
