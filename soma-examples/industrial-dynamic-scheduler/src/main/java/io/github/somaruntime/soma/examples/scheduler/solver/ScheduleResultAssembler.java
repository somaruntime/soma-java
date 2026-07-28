package io.github.somaruntime.soma.examples.scheduler.solver;

import io.github.somaruntime.soma.examples.scheduler.result.ScheduleChecksum;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduledOperation;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleResult;
import io.github.somaruntime.soma.examples.scheduler.runtime.SchedulerRuntime;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationAssignment;

import java.util.ArrayList;
import java.util.List;

/** SOMA schema record 到 detached application result 的唯一转换边界。 */
final class ScheduleResultAssembler {
  private ScheduleResultAssembler() {
  }

  static ScheduleResult assemble(
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
    return new ScheduleResult(
        assignments,
        summary.completedJobs,
        summary.makespanMinute,
        summary.totalTardinessMinutes,
        summary.weightedTardiness,
        ScheduleChecksum.compute(assignments));
  }
}
