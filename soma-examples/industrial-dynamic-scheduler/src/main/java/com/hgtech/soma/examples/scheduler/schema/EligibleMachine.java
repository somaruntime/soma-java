package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "eligible_machines", defaultCapacity = 24576)
@SomaIndex(name = "by_operation", fields = {
    "operationKey.jobId.value", "operationKey.operationId.value"})
public final class EligibleMachine {
  @SomaField public OperationKey operationKey;
  @SomaField public MachineId machineId;
  @SomaField public long processingMinutes;
}
