package com.example.scheduler.soma.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4096L)
final class TransportTime {
    @SomaKey long routeId;
    @SomaIndex MachineId machineId;
    @SomaField long transportMinutes;
}
