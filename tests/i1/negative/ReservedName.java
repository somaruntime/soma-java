package com.example.soma.i1.negative.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class Soma {
    @SomaKey long id;
    @SomaField long value;
}
