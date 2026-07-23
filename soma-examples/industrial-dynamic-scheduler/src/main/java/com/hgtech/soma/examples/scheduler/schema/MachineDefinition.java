package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import java.util.List;

@SomaTable(name = "machine_definitions", defaultCapacity = 128)
public final class MachineDefinition {
  @SomaKey public MachineId machineId;
  @SomaField public SetupFamilyId initialSetupFamily;
  @SomaChild(initialCapacity = 8)
  public List<MaintenanceWindow> maintenanceWindows;
}
