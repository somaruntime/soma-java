package com.hgtech.soma.examples.scheduler.runtime;

/** 已完成且通过应用 validator 前仍不宣称正确的调度结果摘要。 */
public final class ScheduleResult {
  public final int assignments;
  public final int completedJobs;
  public final long makespanMinute;
  public final long totalTardinessMinutes;
  public final long weightedTardiness;
  public final long processedEvents;
  public final String resultChecksum;

  ScheduleResult(int assignments, int completedJobs, long makespanMinute,
                 long totalTardinessMinutes, long weightedTardiness,
                 long processedEvents, String resultChecksum) {
    this.assignments = assignments;
    this.completedJobs = completedJobs;
    this.makespanMinute = makespanMinute;
    this.totalTardinessMinutes = totalTardinessMinutes;
    this.weightedTardiness = weightedTardiness;
    this.processedEvents = processedEvents;
    this.resultChecksum = resultChecksum;
  }
}
