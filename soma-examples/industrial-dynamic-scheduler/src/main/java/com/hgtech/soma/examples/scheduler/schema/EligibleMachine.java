package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "eligible_machines", defaultCapacity = 8)
public final class EligibleMachine {
  @SomaField public MachineId machineId;
  @SomaField public long processingMinutes;
}
