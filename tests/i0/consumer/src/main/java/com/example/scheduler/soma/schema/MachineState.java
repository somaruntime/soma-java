package com.example.scheduler.soma.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 256L)
final class MachineState {
    @SomaKey MachineId machineId;
    @SomaField boolean enabled;
}
