package com.example.soma.valuekeyed;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable
public final class ValueKeyedMachine {
    @SomaKey
    public MachineId id;

    @SomaField
    public int payload;

    public ValueKeyedMachine() {
    }
}
