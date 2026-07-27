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

  private SolveEvidence(
      long processedEvents,
      String schemaHash,
      String runtimePlanHash,
      SchedulerRuntime.RuntimeEvidence runtime) {
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
  }

  static SolveEvidence capture(
      SchedulerRuntime runtime,
      DispatchSummary summary) {
    return new SolveEvidence(
        summary.processedEvents,
        runtime.schemaHash(),
        runtime.runtimePlanHash(),
        runtime.runtimeEvidence());
  }
}
