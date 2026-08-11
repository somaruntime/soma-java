package io.github.somaruntime.benchmarks.scheduling.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Stable benchmark-only scheduling fixture. */
@SomaTable(defaultCapacity = 1_024)
final class MachineState {
    @SomaKey long machineId;
    @SomaIndex long workCenterId;
    @SomaField long availableMinute;
    @SomaField boolean enabled;
}
