package com.hgtech.soma.examples.scheduler.problem;

/** 机器不可用的半开时间区间。 */
public final class MaintenanceInterval {
  public final long startMinute;
  public final long endMinute;

  public MaintenanceInterval(long startMinute, long endMinute) {
    this.startMinute = startMinute;
    this.endMinute = endMinute;
  }
}
