package io.github.somaruntime.examples.scheduling.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Authoritative runtime state and final schedule of one operation. */
@SomaTable(defaultCapacity = 100_000)
final class OperationState {
    @SomaKey long operationId;
    @SomaIndex long jobId;
    @SomaField long sequence;
    @SomaField long successorOperationId;
    @SomaField boolean hasSuccessor;
    @SomaField long readyTime;
    @SomaField OperationStatus status;
    @SomaField long assignedMachineId;
    @SomaField long processingTime;
    @SomaField long startTime;
    @SomaField long completionTime;
}
