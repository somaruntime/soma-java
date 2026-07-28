package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class SetupTimeKey {
  @SomaField public MachineId machineId;
  @SomaField public SetupFamilyId fromFamily;
  @SomaField public SetupFamilyId toFamily;
}
