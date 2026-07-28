package com.example.soma.floatingvalue;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class FloatingPayload {
    @SomaField float single;
    @SomaField double wide;
}
