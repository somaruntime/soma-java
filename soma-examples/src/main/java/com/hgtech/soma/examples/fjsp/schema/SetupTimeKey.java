package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class SetupTimeKey {
  @SomaField MachineId machineId;
  @SomaField SetupFamilyPair familyPair;
}
