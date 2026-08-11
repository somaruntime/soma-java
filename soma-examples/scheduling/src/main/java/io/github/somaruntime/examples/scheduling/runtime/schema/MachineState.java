package io.github.somaruntime.examples.scheduling.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Runtime state of one machine. Initial available time is always zero. */
@SomaTable(defaultCapacity = 128)
final class MachineState {
    @SomaKey long machineId;
    @SomaField long availableTime;
    @SomaField long lastOperationId;
    @SomaField long scheduledOperationCount;
    @SomaField long waitingOperationCount;
}
