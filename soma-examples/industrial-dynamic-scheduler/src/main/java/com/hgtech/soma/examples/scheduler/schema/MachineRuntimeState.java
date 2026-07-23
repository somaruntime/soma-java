package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "machine_runtime_states", defaultCapacity = 128)
public final class MachineRuntimeState {
  @SomaKey public MachineId machineId;
  @SomaField public long nextAvailableMinute;
  @SomaField @SomaOptional public SetupFamilyId lastSetupFamily;
  @SomaField public long version;
}
