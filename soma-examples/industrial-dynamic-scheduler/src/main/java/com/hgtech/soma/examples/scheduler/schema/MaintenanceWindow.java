package com.hgtech.soma.examples.scheduler.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "maintenance_windows", defaultCapacity = 8)
public final class MaintenanceWindow {
  @SomaField public long startMinute;
  @SomaField public long endMinute;
}
