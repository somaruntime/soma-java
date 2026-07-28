package com.example.soma.compositekeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class OperationState {
    @SomaKey
    public OperationKey key;

    @SomaField
    public int payload;

    public OperationState() {
    }
}
