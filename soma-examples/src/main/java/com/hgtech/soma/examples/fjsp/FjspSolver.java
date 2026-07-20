package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.generated.JobResultBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationAssignmentBatch;
import com.hgtech.soma.runtime.IndexSnapshot;
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
  private boolean solved;

  public FjspSolver(FjspInstance instance,
                    FcfsSptDispatchRule dispatchRule) {
    if (instance == null) throw new NullPointerException("instance");
    if (dispatchRule == null) throw new NullPointerException("dispatchRule");
    this.instance = instance;
    this.machineQueue = new FjspMachineAvailabilityQueue(instance.machines);
    this.frontier = new FjspCandidateFrontier(
      instance, dispatchRule, machineQueue);
  }

  public FjspSolveResult solve() {
    if (solved) throw new IllegalStateException("FJSP solver is one-shot");
    releaseInitialOperations();
    long makespan = 0L;
    long totalTardiness = 0L;
    long checksum = 1L;
    int completedJobs = 0;
    while (instance.assignments.size() < instance.operationCount) {
      Dispatch dispatch = dispatchNext();
      Completion completion = advanceJob(dispatch);
      if (completion.completed) {
        completedJobs++;
        totalTardiness += completion.tardiness;
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
    solved = true;
    return new FjspSolveResult(instance.assignments.size(), completedJobs,
      makespan, totalTardiness, checksum);
  }

  private void releaseInitialOperations() {
    IndexSnapshot jobRows = instance.jobs.rows().sorted((left, right) -> {
      int compared = Long.compare(left.inputOrder(), right.inputOrder());
      return compared != 0 ? compared
        : Long.compare(left.jobIdValue(), right.jobIdValue());
    }).rowIndexes();
    LongColumnView jobIds = instance.jobs.jobIdValueColumn();
    try {
      for (int position = 0; position < jobRows.size(); position++) {
        frontier.release(operationAt(jobIds.getLong(jobRows.indexAt(position)), 0));
      }
    } finally {
      jobIds.close();
    }
  }

  private OperationKey operationAt(long jobId, int sequenceNo) {
    IndexSnapshot rows = instance.definitions.findByJobSequence(
      new JobId(jobId), sequenceNo).rowIndexes();
    require(rows.size() == 1,
      "each job sequence must identify exactly one operation");
    LongColumnView operationIds =
      instance.definitions.operationKeyOperationIdValueColumn();
    try {
      return operationAtRow(jobId, rows.indexAt(0), operationIds);
    } finally {
      operationIds.close();
    }
  }

  private OperationKey operationAtRow(
      long jobId, int row, LongColumnView operationIds) {
    return new OperationKey(
      new JobId(jobId), new OperationId(operationIds.getLong(row)));
  }

  private Dispatch dispatchNext() {
    MachineSelection machine = selectNextMachine();
    FjspCandidateFrontier.Candidate candidate = frontier.select(
      machine.machineId, machine.availableFromMinute,
      machine.lastFamilyPresent, machine.lastFamily);
    long setupStart = maximum(
      machine.availableFromMinute, candidate.baseReadyMinute);
    long start = setupStart + candidate.setupMinutes;
    long end = start + candidate.processingMinutes;
    commit(machine.machineId, candidate, setupStart, start, end);
    return new Dispatch(candidate.jobId, candidate.operationId, end);
  }

  private MachineSelection selectNextMachine() {
    LongColumnView available = instance.machines.availableFromMinuteColumn();
    LongColumnView lastFamily = instance.machines.lastSetupFamilyValueColumn();
    try {
      while (!machineQueue.isEmpty()) {
        int slot = machineQueue.take();
        MachineId machineId = machineQueue.machineId(slot);
        // 其他operation的retire可能使该machine entry变空；到达heap root时惰性淘汰。
        if (instance.frontier.findByMachine(machineId).count() == 0L) continue;
        int row = instance.machines.rowIndexOf(machineId.value);
        long currentAvailable = available.getLong(row);
        require(currentAvailable == machineQueue.availableFromMinute(slot),
          "machine queue must match authoritative table availability");
        boolean present = lastFamily.isPresent(row);
        return new MachineSelection(machineId, currentAvailable, present,
          present ? lastFamily.getLong(row) : 0L);
      }
    } finally {
      lastFamily.close();
      available.close();
    }
    throw new IllegalStateException("no machine has a released candidate");
  }

  private void commit(MachineId machineId,
                      FjspCandidateFrontier.Candidate candidate,
                      long setupStart, long start, long end) {
    // 有序提交而非跨表原子事务；失败后由 instance lifecycle 负责整体丢弃。
    instance.assignments.addBatch(new OperationAssignmentBatch(1).addValues(
      candidate.operationKey, machineId, setupStart, candidate.setupMinutes,
      start, candidate.processingMinutes, end));
    instance.machines.mutate(machineId).setAvailableFromMinute(end)
      .setLastSetupFamily(candidate.targetSetupFamily).commit();
    machineQueue.updateInactive(machineId, end);
    RemoveResult removed = instance.frontier
      .findByOperation(candidate.operationKey).remove();
    require(removed.removed() > 0L,
      "commit must remove all candidates of the assigned operation");
    if (instance.frontier.findByMachine(machineId).count() != 0L) {
      machineQueue.activate(machineId);
    }
  }

  private Completion advanceJob(Dispatch dispatch) {
    int definitionRow = instance.definitions.rowIndexOf(
      dispatch.jobId, dispatch.operationId);
    int sequenceNo;
    IntColumnView sequences = instance.definitions.sequenceNoColumn();
    try {
      sequenceNo = sequences.getInt(definitionRow);
    } finally {
      sequences.close();
    }
    int nextSequence = sequenceNo + 1;
    JobId jobId = new JobId(dispatch.jobId);
    instance.jobStates.mutate(jobId)
      .setNextSequenceNo(nextSequence).commit();
    IndexSnapshot successors = instance.definitions
      .findByJobSequence(jobId, nextSequence).rowIndexes();
    if (successors.size() == 1) {
      LongColumnView operationIds =
        instance.definitions.operationKeyOperationIdValueColumn();
      try {
        OperationKey successor = operationAtRow(
          dispatch.jobId, successors.indexAt(0), operationIds);
        instance.operationStates.mutate(successor)
          .setJobReadyMinute(dispatch.endMinute).commit();
        frontier.release(successor);
        return Completion.incomplete();
      } finally {
        operationIds.close();
      }
    }
    require(successors.size() == 0,
      "job sequence must not contain duplicate operations");
    long dueMinute;
    LongColumnView dueMinutes = instance.jobs.dueMinuteColumn();
    try {
      dueMinute = dueMinutes.getLong(instance.jobs.rowIndexOf(dispatch.jobId));
    } finally {
      dueMinutes.close();
    }
    long tardiness = Math.max(0L, dispatch.endMinute - dueMinute);
    instance.jobResults.addBatch(new JobResultBatch(1).addValues(
      jobId, dispatch.endMinute, tardiness));
    return Completion.completed(tardiness);
  }

  private static long maximum(long left, long right) {
    return left >= right ? left : right;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class MachineSelection {
    final MachineId machineId;
    final long availableFromMinute;
    final boolean lastFamilyPresent;
    final long lastFamily;

    MachineSelection(MachineId machineId, long availableFromMinute,
                     boolean lastFamilyPresent, long lastFamily) {
      this.machineId = machineId;
      this.availableFromMinute = availableFromMinute;
      this.lastFamilyPresent = lastFamilyPresent;
      this.lastFamily = lastFamily;
    }
  }

  private static final class Dispatch {
    final long jobId;
    final long operationId;
    final long endMinute;

    Dispatch(long jobId, long operationId, long endMinute) {
      this.jobId = jobId;
      this.operationId = operationId;
      this.endMinute = endMinute;
    }
  }

  private static final class Completion {
    final boolean completed;
    final long tardiness;

    private Completion(boolean completed, long tardiness) {
      this.completed = completed;
      this.tardiness = tardiness;
    }

    static Completion incomplete() {
      return new Completion(false, 0L);
    }

    static Completion completed(long tardiness) {
      return new Completion(true, tardiness);
    }
  }
}
