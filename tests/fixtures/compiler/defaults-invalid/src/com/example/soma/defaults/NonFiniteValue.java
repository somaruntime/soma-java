package com.example.soma.defaults;

import io.github.somaruntime.soma.annotation.SomaDefault;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class NonFiniteValue {
    @SomaField @SomaDefault("Infinity") float x;
}
