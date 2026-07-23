package com.hgtech.soma.examples.scheduler.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一台机器及其初始状态的不可变输入定义。 */
public final class MachineSpec {
  public final long id;
  public final long initialSetupFamily;
  public final long initialAvailableMinute;
  public final List<MaintenanceInterval> maintenance;

  public MachineSpec(long id, long initialSetupFamily,
                     long initialAvailableMinute,
                     List<MaintenanceInterval> maintenance) {
    if (maintenance == null) throw new NullPointerException("maintenance");
    this.id = id;
    this.initialSetupFamily = initialSetupFamily;
    this.initialAvailableMinute = initialAvailableMinute;
    this.maintenance = Collections.unmodifiableList(
        new ArrayList<MaintenanceInterval>(maintenance));
  }
}
