package io.github.somaruntime.examples.scheduling.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Derived dispatch state: one candidate-machine entry for a READY operation. */
@SomaTable(defaultCapacity = 4_096)
final class MachineWaitingOperation {
    @SomaKey long waitingEntryId;
    @SomaIndex long machineId;
    @SomaIndex long operationId;
    @SomaField long jobId;
    @SomaField long readyTime;
    @SomaField long processingTime;
}
