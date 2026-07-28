package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "eligible_machines", defaultCapacity = 24576)
@SomaIndex(name = "by_operation", fields = {
    "operationKey.jobId.value", "operationKey.operationId.value"})
public final class EligibleMachine {
  @SomaField public OperationKey operationKey;
  @SomaField public MachineId machineId;
  @SomaField public long processingMinutes;
}
