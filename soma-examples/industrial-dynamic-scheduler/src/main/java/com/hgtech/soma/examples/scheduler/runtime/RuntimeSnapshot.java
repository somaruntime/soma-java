package com.hgtech.soma.examples.scheduler.runtime;

/** Runtime 关闭前捕获的 immutable identity 和 high-water 快照。 */
public final class RuntimeSnapshot {
  public final String schemaHash;
  public final String runtimePlanHash;
  public final long exactIndexProbes;
  public final long exactIndexHighWaterBytes;
  public final long updateScratchHighWaterBytes;
  public final long operationScratchHighWaterBytes;
  public final int assignmentCapacity;
  public final int frontierCapacity;
  public final int assignmentKeyCount;

  private RuntimeSnapshot(
      String schemaHash,
      String runtimePlanHash,
      SchedulerRuntime.RuntimeEvidence evidence) {
    this.schemaHash = schemaHash;
    this.runtimePlanHash = runtimePlanHash;
    exactIndexProbes = evidence.exactIndexProbes;
    exactIndexHighWaterBytes = evidence.exactIndexHighWaterBytes;
    updateScratchHighWaterBytes = evidence.updateScratchHighWaterBytes;
    operationScratchHighWaterBytes =
        evidence.operationScratchHighWaterBytes;
    assignmentCapacity = evidence.assignmentCapacity;
    frontierCapacity = evidence.frontierCapacity;
    assignmentKeyCount = evidence.assignmentKeyCount;
  }

  public static RuntimeSnapshot capture(SchedulerRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    return new RuntimeSnapshot(
        runtime.schemaHash(),
        runtime.runtimePlanHash(),
        runtime.runtimeEvidence());
  }
}
