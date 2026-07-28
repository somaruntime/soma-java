package io.github.somaruntime.soma.examples.scheduler.solver;

import io.github.somaruntime.soma.examples.scheduler.schema.JobId;
import io.github.somaruntime.soma.examples.scheduler.schema.MachineId;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationId;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationKey;
import io.github.somaruntime.soma.examples.scheduler.schema.ResourceId;

/** CandidateFrontier 复用的单项 selected scratch。 */
final class SelectedCandidate {
  boolean present;
  long jobId;
  long operationId;
  long machineId;
  long targetSetupFamily;
  long resourceId;
  int resourceUnits;
  long baseReadyMinute;
  long processingMinutes;
  long setupMinutes;
  long transportMinutes;
  long effectiveStartMinute;
  long completionMinute;
  long dueMinute;
  int priority;
  long operationVersion;
  long machineVersion;
  long resourceVersion;

  void clear() {
    present = false;
  }

  void copyFrom(SelectedCandidate source) {
    present = source.present;
    jobId = source.jobId;
    operationId = source.operationId;
    machineId = source.machineId;
    targetSetupFamily = source.targetSetupFamily;
    resourceId = source.resourceId;
    resourceUnits = source.resourceUnits;
    baseReadyMinute = source.baseReadyMinute;
    processingMinutes = source.processingMinutes;
    setupMinutes = source.setupMinutes;
    transportMinutes = source.transportMinutes;
    effectiveStartMinute = source.effectiveStartMinute;
    completionMinute = source.completionMinute;
    dueMinute = source.dueMinute;
    priority = source.priority;
    operationVersion = source.operationVersion;
    machineVersion = source.machineVersion;
    resourceVersion = source.resourceVersion;
  }

  OperationKey operation() {
    return new OperationKey(
        new JobId(jobId), new OperationId(operationId));
  }

  MachineId machine() {
    return new MachineId(machineId);
  }

  ResourceId resource() {
    return new ResourceId(resourceId);
  }
}
