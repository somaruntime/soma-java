package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "operation_assignments", defaultCapacity = 8192)
@SomaIndex(name = "by_machine", fields = {"machineId.value"})
@SomaIndex(name = "by_resource", fields = {"resourceId.value"})
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
