package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;

/** 仅供示例内部验真入口读取的单次执行证据，不属于领域 Result。 */
final class SolveEvidence {
  final long processedEvents;
  final String schemaHash;
  final String runtimePlanHash;
  final long exactIndexProbes;
  final long exactIndexHighWaterBytes;
  final long updateScratchHighWaterBytes;
  final long operationScratchHighWaterBytes;
  final int assignmentCapacity;
  final int frontierCapacity;
  final int assignmentKeyCount;
  final String dataFlowDefinitionIdentity;
  final String dataFlowTemplateIdentity;
  final String dataFlowPolicyIdentity;
  final long dataFlowBoundSources;
  final long dataFlowScanned;
  final long dataFlowMatched;
  final long dataFlowOutputElements;
  final int dataFlowTasks;
  final int dataFlowWorkers;

  private SolveEvidence(
      long processedEvents,
      String schemaHash,
      String runtimePlanHash,
      SchedulerRuntime.RuntimeEvidence runtime,
      AssignmentSummaryFlow.Evidence dataFlow) {
    this.processedEvents = processedEvents;
    this.schemaHash = schemaHash;
    this.runtimePlanHash = runtimePlanHash;
    exactIndexProbes = runtime.exactIndexProbes;
    exactIndexHighWaterBytes = runtime.exactIndexHighWaterBytes;
    updateScratchHighWaterBytes = runtime.updateScratchHighWaterBytes;
    operationScratchHighWaterBytes =
        runtime.operationScratchHighWaterBytes;
    assignmentCapacity = runtime.assignmentCapacity;
    frontierCapacity = runtime.frontierCapacity;
    assignmentKeyCount = runtime.assignmentKeyCount;
    dataFlowDefinitionIdentity = dataFlow.definitionIdentity;
    dataFlowTemplateIdentity = dataFlow.templateIdentity;
    dataFlowPolicyIdentity = dataFlow.policyIdentity;
    dataFlowBoundSources = dataFlow.boundSources;
    dataFlowScanned = dataFlow.scanned;
    dataFlowMatched = dataFlow.matched;
    dataFlowOutputElements = dataFlow.outputElements;
    dataFlowTasks = dataFlow.tasks;
    dataFlowWorkers = dataFlow.workers;
  }

  static SolveEvidence capture(
      SchedulerRuntime runtime,
      DispatchSummary summary,
      AssignmentSummaryFlow.Evidence dataFlow) {
    if (dataFlow == null) throw new NullPointerException("dataFlow");
    return new SolveEvidence(
        summary.processedEvents,
        runtime.schemaHash(),
        runtime.runtimePlanHash(),
        runtime.runtimeEvidence(),
        dataFlow);
  }
}
