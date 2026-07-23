package com.hgtech.soma.examples.scheduler.result;

import com.hgtech.soma.examples.scheduler.runtime.DispatchSummary;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;

import java.util.ArrayList;
import java.util.List;

/** SOMA schema record 到 detached application result 的唯一转换边界。 */
public final class ScheduleResultAssembler {
  private ScheduleResultAssembler() {
  }

  public static ScheduleResult assemble(
      SchedulerRuntime runtime, DispatchSummary summary) {
    List<OperationAssignment> source = runtime.exportAssignments();
    ArrayList<ScheduledOperation> assignments =
        new ArrayList<ScheduledOperation>(source.size());
    for (OperationAssignment value : source) {
      assignments.add(new ScheduledOperation(
          value.operationKey.jobId.value,
          value.operationKey.operationId.value,
          value.machineId.value,
          value.resourceId.value,
          value.setupFamily.value,
          value.setupStartMinute,
          value.setupMinutes,
          value.transportMinutes,
          value.startMinute,
          value.processingMinutes,
          value.endMinute,
          value.dueMinute,
          value.priority));
    }
    SchedulerRuntime.RuntimeEvidence evidence = runtime.runtimeEvidence();
    SolveDiagnostics diagnostics = new SolveDiagnostics(
        runtime.schemaHash(),
        runtime.runtimePlanHash(),
        evidence.exactIndexProbes,
        evidence.exactIndexHighWaterBytes,
        evidence.updateScratchHighWaterBytes,
        evidence.operationScratchHighWaterBytes,
        evidence.assignmentCapacity,
        evidence.frontierCapacity,
        evidence.assignmentKeyCount);
    return new ScheduleResult(
        assignments,
        summary.completedJobs,
        summary.makespanMinute,
        summary.totalTardinessMinutes,
        summary.weightedTardiness,
        summary.processedEvents,
        ScheduleChecksum.compute(assignments),
        diagnostics);
  }
}
