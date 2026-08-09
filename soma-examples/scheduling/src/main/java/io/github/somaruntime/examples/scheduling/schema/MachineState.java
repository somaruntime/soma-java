package io.github.somaruntime.examples.scheduling.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 1_024L)
final class MachineState {
    @SomaKey long machineId;
    @SomaIndex long workCenterId;
    @SomaField long availableMinute;
    @SomaField boolean enabled;
}
