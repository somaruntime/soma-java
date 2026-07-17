package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "operation_assignments", defaultCapacity = 4096)
public final class OperationAssignment {
  @SomaKey public OperationKey operationKey;
  @SomaField public MachineId assignedMachine;
  @SomaField public long setupStartMinute;
  @SomaField public long setupMinutes;
  @SomaField public long startMinute;
  @SomaField public long processingMinutes;
  @SomaField public long endMinute;
}
