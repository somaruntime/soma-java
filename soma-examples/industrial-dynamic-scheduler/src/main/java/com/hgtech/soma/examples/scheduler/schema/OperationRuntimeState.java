package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "operation_runtime_states", defaultCapacity = 8192)
public final class OperationRuntimeState {
  @SomaKey public OperationKey operationKey;
  @SomaField public OperationStatus status;
  @SomaField public long predecessorEndMinute;
  @SomaField @SomaOptional public MachineId predecessorMachine;
  @SomaField public long version;
}
