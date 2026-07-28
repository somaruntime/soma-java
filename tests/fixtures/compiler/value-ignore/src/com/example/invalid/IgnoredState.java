package com.example.invalid;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIgnore;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class IgnoredState {
    @SomaField
    int value;

    @SomaIgnore
    int cache;
}
