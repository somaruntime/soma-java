package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaOptional;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "operation_runtime_states", defaultCapacity = 8192)
public final class OperationRuntimeState {
  @SomaKey public OperationKey operationKey;
  @SomaField public OperationStatus status;
  @SomaField public long predecessorEndMinute;
  @SomaField @SomaOptional public MachineId predecessorMachine;
  @SomaField public long version;
}
