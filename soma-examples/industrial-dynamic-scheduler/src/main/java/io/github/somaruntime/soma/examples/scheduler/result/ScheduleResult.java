package io.github.somaruntime.soma.examples.scheduler.result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runtime 关闭后仍可完整使用的不可变调度结果。 */
public final class ScheduleResult {
  private final List<ScheduledOperation> assignments;
  public final int completedJobs;
  public final long makespanMinute;
  public final long totalTardinessMinutes;
  public final long weightedTardiness;
  public final String resultChecksum;

  public ScheduleResult(
      List<ScheduledOperation> assignments, int completedJobs,
      long makespanMinute, long totalTardinessMinutes,
      long weightedTardiness, String resultChecksum) {
    if (assignments == null || resultChecksum == null) {
      throw new NullPointerException("assignments and resultChecksum");
    }
    this.assignments = Collections.unmodifiableList(
        new ArrayList<ScheduledOperation>(assignments));
    this.completedJobs = completedJobs;
    this.makespanMinute = makespanMinute;
    this.totalTardinessMinutes = totalTardinessMinutes;
    this.weightedTardiness = weightedTardiness;
    this.resultChecksum = resultChecksum;
  }

  public List<ScheduledOperation> assignments() {
    return assignments;
  }

  public int assignmentCount() {
    return assignments.size();
  }
}
