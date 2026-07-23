package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class MachinePairKey {
  @SomaField public MachineId fromMachine;
  @SomaField public MachineId toMachine;
}
