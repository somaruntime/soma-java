package com.example.phase5.imported;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class ImportedModifiersValue {
    @SomaField
    @SomaKey
    long keyed;

    @SomaField
    @SomaChild
    long child;

    @SomaField
    @SomaOptional
    Long optional;
}
