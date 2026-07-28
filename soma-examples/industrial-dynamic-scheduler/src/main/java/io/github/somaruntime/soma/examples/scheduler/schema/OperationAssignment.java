package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "operation_assignments", defaultCapacity = 8192)
public final class OperationAssignment {
  @SomaKey public OperationKey operationKey;
  @SomaField public MachineId machineId;
  @SomaField public ResourceId resourceId;
  @SomaField public SetupFamilyId setupFamily;
  @SomaField public long setupStartMinute;
  @SomaField public long setupMinutes;
  @SomaField public long transportMinutes;
  @SomaField public long startMinute;
  @SomaField public long processingMinutes;
  @SomaField public long endMinute;
  @SomaField public long dueMinute;
  @SomaField public int priority;
}
