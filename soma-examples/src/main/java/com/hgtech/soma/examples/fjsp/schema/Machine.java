package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaDefault;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaIndex;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaOptional;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "machines", defaultCapacity = 128)
@SomaIndex(name = "by_state", fields = {"state"})
public final class Machine {
  @SomaKey public MachineId machineId;
  @SomaField @SomaDefault("READY") public MachineState state;
  @SomaField @SomaDefault("0") public long availableFromMinute;
  @SomaField @SomaOptional public SetupFamilyId lastSetupFamily;
}
