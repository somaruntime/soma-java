package com.hgtech.soma.examples.fjsp;

/** 与 schema/runtime 诊断解耦的领域求解结果。 */
public final class FjspSolveResult {
  public final int assignments;
  public final int completedJobs;
  public final long makespan;
  public final long totalTardiness;
  public final long checksum;

  FjspSolveResult(int assignments, int completedJobs, long makespan,
                  long totalTardiness, long checksum) {
    this.assignments = assignments;
    this.completedJobs = completedJobs;
    this.makespan = makespan;
    this.totalTardiness = totalTardiness;
    this.checksum = checksum;
  }

  @Override
  public String toString() {
    return "FjspSolveResult{assignments=" + assignments
      + ", completedJobs=" + completedJobs
      + ", makespan=" + makespan
      + ", totalTardiness=" + totalTardiness
      + ", checksum=" + checksum + "}";
  }
}
