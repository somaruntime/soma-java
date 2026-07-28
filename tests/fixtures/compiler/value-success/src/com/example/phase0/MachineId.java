package com.example.phase0;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class MachineId {
    @SomaField
    long value;

    public static MachineId of(long value) {
        return new MachineId(value);
    }
}
