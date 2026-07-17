package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "candidate_machine_definitions", defaultCapacity = 8)
public final class CandidateMachineDefinition {
  @SomaField public MachineId machineId;
  @SomaField public long processingMinutes;
}
