package com.example.phase0;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class OperationKey {
    @SomaField(name = "machine_id")
    MachineId machineId;

    @SomaField
    int sequence;

    @SomaField
    double score;

    @SomaField
    String label;

    @SomaField
    OperationState state;

    static final String DEBUG_KIND = "operation-key";
}
