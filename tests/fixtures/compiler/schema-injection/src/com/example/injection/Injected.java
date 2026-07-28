package com.example.injection;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class Injected {
    @SomaField
    int value;
}
