package com.hgtech.soma.examples.scheduler.runtime;

/** 调度循环产出的轻量内部摘要，不负责结果物化或校验。 */
public final class DispatchSummary {
  public final int assignments;
  public final int completedJobs;
  public final long makespanMinute;
  public final long totalTardinessMinutes;
  public final long weightedTardiness;
  public final long processedEvents;

  public DispatchSummary(
      int assignments, int completedJobs, long makespanMinute,
      long totalTardinessMinutes, long weightedTardiness,
      long processedEvents) {
    this.assignments = assignments;
    this.completedJobs = completedJobs;
    this.makespanMinute = makespanMinute;
    this.totalTardinessMinutes = totalTardinessMinutes;
    this.weightedTardiness = weightedTardiness;
    this.processedEvents = processedEvents;
  }
}
