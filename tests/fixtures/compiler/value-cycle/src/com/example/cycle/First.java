package com.example.cycle;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class First {
    @SomaField
    Second second;
}
