package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.OperationId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.examples.scheduler.schema.OperationStatus;
import com.hgtech.soma.examples.scheduler.schema.ResourceId;
import com.hgtech.soma.examples.scheduler.schema.SetupFamilyId;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentBatch;

/** assignment append、authoritative state mutation 与 successor 发布边界。 */
final class AssignmentCommitter {
  private final SchedulerRuntime runtime;
  private final CandidateFrontier frontier;
  private final OperationAssignmentBatch assignmentBatch =
      new OperationAssignmentBatch(1);
  private long makespan;
  private int completedJobs;

  AssignmentCommitter(
      SchedulerRuntime runtime, CandidateFrontier frontier) {
    this.runtime = runtime;
    this.frontier = frontier;
  }

  void commit(SelectedCandidate selected, long setupStart) {
    OperationKey operation = selected.operation();
    MachineId machine = selected.machine();
    ResourceId resource = selected.resource();

    assignmentBatch.clear();
    assignmentBatch.addValues(
        operation, machine, resource,
        new SetupFamilyId(selected.targetSetupFamily), setupStart,
        selected.setupMinutes, selected.transportMinutes,
        selected.effectiveStartMinute, selected.processingMinutes,
        selected.completionMinute, selected.dueMinute,
        selected.priority);
    runtime.assignments().addBatch(assignmentBatch);

    runtime.machineStates().mutate(machine)
        .setNextAvailableMinute(selected.completionMinute)
        .setLastSetupFamily(
            new SetupFamilyId(selected.targetSetupFamily))
        .setVersion(Math.addExact(selected.machineVersion, 1L))
        .commit();

    frontier.commitResource(
        resource,
        selected.effectiveStartMinute,
        selected.completionMinute,
        selected.resourceUnits);
    runtime.resourceStates().mutate(resource)
        .setVersion(Math.addExact(selected.resourceVersion, 1L))
        .commit();

    runtime.operationStates().mutate(operation)
        .setStatus(OperationStatus.SCHEDULED)
        .setVersion(Math.addExact(selected.operationVersion, 1L))
        .commit();

    frontier.retireOperation(operation);
    frontier.refreshMachine(machine);
    releaseSuccessor(
        operation, machine, selected.completionMinute, selected);
    makespan = Math.max(makespan, selected.completionMinute);
  }

  int completedJobs() { return completedJobs; }
  long makespan() { return makespan; }

  private void releaseSuccessor(
      OperationKey operation,
      MachineId machine,
      long predecessorEnd,
      SelectedCandidate selected) {
    int next = Math.addExact(
        frontier.operationSequence(operation), 1);
    long successorId = frontier.operationIdAt(
        operation.jobId.value, next);
    if (successorId != Long.MIN_VALUE) {
      frontier.releaseOperation(
          new OperationKey(
              operation.jobId, new OperationId(successorId)),
          predecessorEnd, machine);
      return;
    }
    completedJobs = Math.addExact(completedJobs, 1);
  }
}
