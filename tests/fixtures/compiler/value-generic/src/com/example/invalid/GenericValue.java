package com.example.invalid;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class GenericValue<T> {
    @SomaField
    int value;
}
