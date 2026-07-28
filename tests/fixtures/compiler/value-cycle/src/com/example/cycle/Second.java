package com.example.cycle;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class Second {
    @SomaField
    First first;
}
