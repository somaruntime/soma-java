package io.github.somaruntime.examples.realtimedispatch.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4_096L)
final class MachineRuntime {
    @SomaKey long machineId;
    @SomaIndex String zone;
    @SomaField long availableMinute;
    @SomaField long workloadMinutes;
    @SomaField boolean enabled;
}
