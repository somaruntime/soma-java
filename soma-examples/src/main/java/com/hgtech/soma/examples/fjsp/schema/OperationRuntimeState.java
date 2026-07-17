package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "operation_runtime_states", defaultCapacity = 4096)
public final class OperationRuntimeState {
  @SomaKey public OperationKey operationKey;
  @SomaField public long jobReadyMinute;
  @SomaField public long materialReadyMinute;
}
