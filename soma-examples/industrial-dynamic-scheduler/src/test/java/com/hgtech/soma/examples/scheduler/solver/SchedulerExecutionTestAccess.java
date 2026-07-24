package com.hgtech.soma.examples.scheduler.solver;

/**
 * benchmark/verification 对 package-private solve evidence 的唯一 test 入口。
 *
 * <p>该类型只存在于 test classes，不进入 production JAR。</p>
 */
public final class SchedulerExecutionTestAccess {
  private SchedulerExecutionTestAccess() {
  }

  public static Evidence capture(SchedulingSession session) {
    if (!(session instanceof SomaSchedulingSession)) {
      throw new IllegalArgumentException(
          "unsupported scheduling session implementation");
    }
    SolveEvidence source =
        ((SomaSchedulingSession) session).evidence();
    return new Evidence(source);
  }

  public static final class Evidence {
    public final long processedEvents;
    public final String schemaHash;
    public final String runtimePlanHash;
    public final long exactIndexProbes;
    public final long exactIndexHighWaterBytes;
    public final long updateScratchHighWaterBytes;
    public final long operationScratchHighWaterBytes;
    public final int assignmentCapacity;
    public final int frontierCapacity;
    public final int assignmentKeyCount;

    private Evidence(SolveEvidence source) {
      processedEvents = source.processedEvents;
      schemaHash = source.schemaHash;
      runtimePlanHash = source.runtimePlanHash;
      exactIndexProbes = source.exactIndexProbes;
      exactIndexHighWaterBytes = source.exactIndexHighWaterBytes;
      updateScratchHighWaterBytes =
          source.updateScratchHighWaterBytes;
      operationScratchHighWaterBytes =
          source.operationScratchHighWaterBytes;
      assignmentCapacity = source.assignmentCapacity;
      frontierCapacity = source.frontierCapacity;
      assignmentKeyCount = source.assignmentKeyCount;
    }
  }
}
