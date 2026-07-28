package com.example.invalid;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class InvalidValue {
    @SomaField
    int value;

    @Override
    public int hashCode() {
        return value;
    }
}
