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
    public final String dataFlowDefinitionIdentity;
    public final String dataFlowTemplateIdentity;
    public final String dataFlowPolicyIdentity;
    public final long dataFlowBoundSources;
    public final long dataFlowScanned;
    public final long dataFlowMatched;
    public final long dataFlowOutputElements;
    public final int dataFlowTasks;
    public final int dataFlowWorkers;

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
      dataFlowDefinitionIdentity =
          source.dataFlowDefinitionIdentity;
      dataFlowTemplateIdentity =
          source.dataFlowTemplateIdentity;
      dataFlowPolicyIdentity = source.dataFlowPolicyIdentity;
      dataFlowBoundSources = source.dataFlowBoundSources;
      dataFlowScanned = source.dataFlowScanned;
      dataFlowMatched = source.dataFlowMatched;
      dataFlowOutputElements = source.dataFlowOutputElements;
      dataFlowTasks = source.dataFlowTasks;
      dataFlowWorkers = source.dataFlowWorkers;
    }
  }
}
