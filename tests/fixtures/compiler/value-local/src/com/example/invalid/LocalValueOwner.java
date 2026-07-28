package com.example.invalid;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

public final class LocalValueOwner {
    public void declareLocalValue() {
        @SomaValue
        class LocalValue {
            @SomaField
            int value;
        }
        new LocalValue();
    }
}
