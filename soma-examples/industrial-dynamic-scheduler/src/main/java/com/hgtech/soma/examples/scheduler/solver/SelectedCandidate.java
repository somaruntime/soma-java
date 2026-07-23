package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.schema.DispatchCandidateKey;
import com.hgtech.soma.examples.scheduler.schema.JobId;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.OperationId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.examples.scheduler.schema.ResourceId;
import com.hgtech.soma.examples.scheduler.schema.generated.DispatchCandidateCursor;

/** CandidateFrontier 复用的单项 selected scratch。 */
final class SelectedCandidate {
  boolean present;
  long jobId;
  long operationId;
  long machineId;
  long targetSetupFamily;
  long resourceId;
  int resourceUnits;
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

  void copy(DispatchCandidateCursor value) {
    present = true;
    jobId = value.candidateKeyOperationKeyJobIdValue();
    operationId = value.candidateKeyOperationKeyOperationIdValue();
    machineId = value.candidateKeyMachineIdValue();
    targetSetupFamily = value.targetSetupFamilyValue();
    resourceId = value.requiredResourceValue();
    resourceUnits = value.requiredResourceUnits();
    processingMinutes = value.processingMinutes();
    setupMinutes = value.setupMinutes();
    transportMinutes = value.transportMinutes();
    effectiveStartMinute = value.effectiveStartMinute();
    completionMinute = value.completionMinute();
    dueMinute = value.dueMinute();
    priority = value.priority();
    operationVersion = value.operationVersion();
    machineVersion = value.machineVersion();
    resourceVersion = value.resourceVersion();
  }

  DispatchCandidateKey key() {
    return new DispatchCandidateKey(operation(), machine());
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
