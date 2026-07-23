package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class SetupTimeKey {
  @SomaField public MachineId machineId;
  @SomaField public SetupFamilyId fromFamily;
  @SomaField public SetupFamilyId toFamily;
}
