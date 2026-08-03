package com.example.soma.i1.soma.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 2L)
final class WorkItem {
    @SomaKey long id;
    @SomaField long duration;
}
