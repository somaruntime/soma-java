package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.MachineState;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.generated.JobResultBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationAssignmentBatch;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.RemoveResult;

/**
 * FJSP release/select/commit/successor-release 的唯一求解核心。
 *
 * <p>跨表提交顺序属于 solver。SOMA V1 不提供跨 root table transaction；任一步失败时
 * 调用方应丢弃当前 instance，而不是继续使用部分提交的状态。</p>
 */
public final class FjspSolver {
  private final FjspInstance instance;
  private final FjspCandidateFrontier frontier;
  private final FjspMachineAvailabilityQueue machineQueue;
  private final OperationAssignmentBatch assignmentBatch =
    new OperationAssignmentBatch(1);
  private final JobResultBatch jobResultBatch = new JobResultBatch(1);
  private final FjspReleasedMachines releasedMachines;
  private final MachineSelection selectedMachine = new MachineSelection();
  private final Dispatch dispatch = new Dispatch();
  private boolean completedJob;
  private long completedJobTardiness;
  private boolean solved;

  public FjspSolver(FjspInstance instance,
                    FcfsSptDispatchRule dispatchRule) {
    if (instance == null) throw new NullPointerException("instance");
    if (dispatchRule == null) throw new NullPointerException("dispatchRule");
    this.instance = instance;
    this.machineQueue = new FjspMachineAvailabilityQueue(instance.machines);
    this.frontier = new FjspCandidateFrontier(instance, dispatchRule);
    this.releasedMachines = new FjspReleasedMachines(
      instance.maximumCandidatesPerOperation);
  }

  public FjspSolveResult solve() {
    if (solved) throw new IllegalStateException("FJSP solver is one-shot");
    solved = true;
    releaseInitialOperations();
    long makespan = 0L;
    long totalTardiness = 0L;
    long checksum = 1L;
    int completedJobs = 0;
    while (instance.assignments.size() < instance.operationCount) {
      dispatchNext();
      advanceJob();
      refreshQueueMembership(dispatch.machineId);
      if (completedJob) {
        completedJobs = Math.addExact(completedJobs, 1);
        totalTardiness = Math.addExact(
          totalTardiness, completedJobTardiness);
      }
      makespan = Math.max(makespan, dispatch.endMinute);
      checksum = 31L * checksum + dispatch.jobId;
      checksum = 31L * checksum + dispatch.operationId;
      checksum = 31L * checksum + dispatch.endMinute;
    }
    require(instance.jobResults.size() == instance.jobCount,
      "all jobs must be completed");
    require(instance.frontier.size() == 0,
      "frontier must be empty after solve");
    return new FjspSolveResult(instance.assignments.size(), completedJobs,
      makespan, totalTardiness, checksum);
  }

  private void releaseInitialOperations() {
    IndexSnapshot jobIndexes = instance.jobs.sorted((left, right) -> {
      int compared = Long.compare(left.inputOrder(), right.inputOrder());
      return compared != 0 ? compared
        : Long.compare(left.jobIdValue(), right.jobIdValue());
    }).indexSnapshot();
    LongColumnView jobIds = instance.jobs.jobIdValueColumn();
    try {
      for (int position = 0; position < jobIndexes.size(); position++) {
        frontier.release(
          operationAt(jobIds.getLong(jobIndexes.indexAt(position)), 0),
          releasedMachines);
        refreshQueueMembership(null);
      }
    } finally {
      jobIds.close();
    }
  }

  private OperationKey operationAt(long jobId, int sequenceNo) {
    int row = instance.definitions.requireIndexByJobSequence(
      new JobId(jobId), sequenceNo);
    LongColumnView operationIds =
      instance.definitions.operationKeyOperationIdValueColumn();
    try {
      return operationAtRow(jobId, row, operationIds);
    } finally {
      operationIds.close();
    }
  }

  private OperationKey operationAtRow(
      long jobId, int row, LongColumnView operationIds) {
    return new OperationKey(
      new JobId(jobId), new OperationId(operationIds.getLong(row)));
  }

  private void dispatchNext() {
    selectNextMachine();
    MachineSelection machine = selectedMachine;
    FjspCandidateFrontier.Candidate candidate = frontier.select(
      machine.machineId, machine.availableFromMinute,
      machine.lastFamilyPresent, machine.lastFamily);
    long setupStart = maximum(
      machine.availableFromMinute, candidate.baseReadyMinute);
    long start = Math.addExact(setupStart, candidate.setupMinutes);
    long end = Math.addExact(start, candidate.processingMinutes);
    commit(machine.machineId, candidate, setupStart, start, end);
    dispatch.machineId = machine.machineId;
    dispatch.jobId = candidate.jobId;
    dispatch.operationId = candidate.operationId;
    dispatch.endMinute = end;
  }

  private void selectNextMachine() {
    LongColumnView available = instance.machines.availableFromMinuteColumn();
    LongColumnView lastFamily = instance.machines.lastSetupFamilyValueColumn();
    EnumColumnView<MachineState> states = instance.machines.stateColumn();
    try {
      while (!machineQueue.isEmpty()) {
        int slot = machineQueue.take();
        MachineId machineId = machineQueue.machineId(slot);
        // 其他operation的retire可能使该machine entry变空；到达heap root时惰性淘汰。
        if (instance.frontier.scanByMachine(machineId).count() == 0L) continue;
        int row = instance.machines.requireIndex(machineId.value);
        if (states.get(row) != MachineState.READY) continue;
        long currentAvailable = available.getLong(row);
        require(currentAvailable == machineQueue.availableFromMinute(slot),
          "machine queue must match authoritative table availability");
        boolean present = lastFamily.isPresent(row);
        selectedMachine.machineId = machineId;
        selectedMachine.availableFromMinute = currentAvailable;
        selectedMachine.lastFamilyPresent = present;
        selectedMachine.lastFamily = present ? lastFamily.getLong(row) : 0L;
        return;
      }
    } finally {
      states.close();
      lastFamily.close();
      available.close();
    }
    throw new FjspInfeasibleException(
      "operations remain but no machine has an eligible released candidate");
  }

  private void commit(MachineId machineId,
                      FjspCandidateFrontier.Candidate candidate,
                      long setupStart, long start, long end) {
    // 有序提交而非跨表原子事务；失败后由 instance lifecycle 负责整体丢弃。
    assignmentBatch.clear();
    assignmentBatch.addValues(
      candidate.operationKey, machineId, setupStart, candidate.setupMinutes,
      start, candidate.processingMinutes, end);
    instance.assignments.addBatch(assignmentBatch);
    instance.machines.mutate(machineId).setAvailableFromMinute(end)
      .setLastSetupFamily(candidate.targetSetupFamily).commit();
    RemoveResult removed = instance.frontier
      .scanByOperation(candidate.operationKey).remove();
    require(removed.removed() > 0L,
      "commit must remove all candidates of the assigned operation");
  }

  private void advanceJob() {
    completedJob = false;
    completedJobTardiness = 0L;
    releasedMachines.reset();
    int definitionRow = instance.definitions.requireIndex(
      dispatch.jobId, dispatch.operationId);
    int sequenceNo;
    IntColumnView sequences = instance.definitions.sequenceNoColumn();
    try {
      sequenceNo = sequences.getInt(definitionRow);
    } finally {
      sequences.close();
    }
    int nextSequence = Math.addExact(sequenceNo, 1);
    JobId jobId = new JobId(dispatch.jobId);
    instance.jobStates.mutate(jobId)
      .setNextSequenceNo(nextSequence).commit();
    int successorIndex = instance.definitions
      .findIndexByJobSequence(jobId, nextSequence);
    if (successorIndex >= 0) {
      LongColumnView operationIds =
        instance.definitions.operationKeyOperationIdValueColumn();
      try {
        OperationKey successor = operationAtRow(
          dispatch.jobId, successorIndex, operationIds);
        instance.operationStates.mutate(successor)
          .setJobReadyMinute(dispatch.endMinute).commit();
        frontier.release(successor, releasedMachines);
        return;
      } finally {
        operationIds.close();
      }
    }
    long dueMinute;
    LongColumnView dueMinutes = instance.jobs.dueMinuteColumn();
    try {
      dueMinute = dueMinutes.getLong(instance.jobs.requireIndex(dispatch.jobId));
    } finally {
      dueMinutes.close();
    }
    long tardiness = Math.max(
      0L, Math.subtractExact(dispatch.endMinute, dueMinute));
    jobResultBatch.clear();
    jobResultBatch.addValues(jobId, dispatch.endMinute, tardiness);
    instance.jobResults.addBatch(jobResultBatch);
    completedJob = true;
    completedJobTardiness = tardiness;
  }

  private void refreshQueueMembership(MachineId committedMachine) {
    LongColumnView available = instance.machines.availableFromMinuteColumn();
    EnumColumnView<MachineState> states = instance.machines.stateColumn();
    try {
      if (committedMachine != null) {
        refreshQueueMembership(committedMachine, available, states);
      }
      for (int index = 0; index < releasedMachines.size(); index++) {
        long machineId = releasedMachines.machineIdValue(index);
        if (committedMachine == null
            || committedMachine.value != machineId) {
          refreshQueueMembership(new MachineId(machineId), available, states);
        }
      }
    } finally {
      states.close();
      available.close();
    }
  }

  private void refreshQueueMembership(
      MachineId machineId, LongColumnView available,
      EnumColumnView<MachineState> states) {
    int row = instance.machines.requireIndex(machineId.value);
    boolean eligible = states.get(row) == MachineState.READY
      && instance.frontier.scanByMachine(machineId).count() != 0L;
    machineQueue.refresh(machineId, available.getLong(row), eligible);
  }

  private static long maximum(long left, long right) {
    return left >= right ? left : right;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class MachineSelection {
    MachineId machineId;
    long availableFromMinute;
    boolean lastFamilyPresent;
    long lastFamily;
  }

  private static final class Dispatch {
    MachineId machineId;
    long jobId;
    long operationId;
    long endMinute;
  }
}
