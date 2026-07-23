package com.hgtech.soma.examples.scheduler.result;

/** 单次求解产生的不可变 runtime 诊断快照。 */
public final class SolveDiagnostics {
  public final String schemaHash;
  public final String runtimePlanHash;
  public final long exactIndexProbes;
  public final long exactIndexHighWaterBytes;
  public final long updateScratchHighWaterBytes;
  public final long operationScratchHighWaterBytes;
  public final int assignmentCapacity;
  public final int frontierCapacity;
  public final int assignmentKeyCount;

  public SolveDiagnostics(
      String schemaHash, String runtimePlanHash, long exactIndexProbes,
      long exactIndexHighWaterBytes, long updateScratchHighWaterBytes,
      long operationScratchHighWaterBytes, int assignmentCapacity,
      int frontierCapacity, int assignmentKeyCount) {
    if (schemaHash == null || runtimePlanHash == null) {
      throw new NullPointerException("schemaHash and runtimePlanHash");
    }
    this.schemaHash = schemaHash;
    this.runtimePlanHash = runtimePlanHash;
    this.exactIndexProbes = exactIndexProbes;
    this.exactIndexHighWaterBytes = exactIndexHighWaterBytes;
    this.updateScratchHighWaterBytes = updateScratchHighWaterBytes;
    this.operationScratchHighWaterBytes = operationScratchHighWaterBytes;
    this.assignmentCapacity = assignmentCapacity;
    this.frontierCapacity = frontierCapacity;
    this.assignmentKeyCount = assignmentKeyCount;
  }
}
