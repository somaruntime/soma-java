package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "machine_runtime_states", defaultCapacity = 128)
public final class MachineRuntimeState {
  @SomaKey public MachineId machineId;
  @SomaField public long nextAvailableMinute;
  @SomaField public SetupFamilyId lastSetupFamily;
  @SomaField public long version;
}
