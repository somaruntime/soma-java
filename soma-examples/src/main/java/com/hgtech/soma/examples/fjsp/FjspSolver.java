package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.generated.JobResultBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationAssignmentBatch;
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
  private boolean solved;

  public FjspSolver(FjspInstance instance,
                    FcfsSptDispatchRule dispatchRule) {
    if (instance == null) throw new NullPointerException("instance");
    if (dispatchRule == null) throw new NullPointerException("dispatchRule");
    this.instance = instance;
    this.frontier = new FjspCandidateFrontier(instance, dispatchRule);
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
    int[] jobRows = instance.jobs.byDispatchOrder().rowIndexes();
    LongColumnView jobIds = instance.jobs.jobIdValueColumn();
    try {
      for (int row : jobRows) frontier.release(operationAt(jobIds.getLong(row), 0));
    } finally {
      jobIds.close();
    }
  }

  private OperationKey operationAt(long jobId, int sequenceNo) {
    int[] rows = instance.definitions.findByJobSequence(
      new JobId(jobId), sequenceNo).rowIndexes();
    require(rows.length == 1,
      "each job sequence must identify exactly one operation");
    LongColumnView operationIds =
      instance.definitions.operationKeyOperationIdValueColumn();
    try {
      return new OperationKey(
        new JobId(jobId), new OperationId(operationIds.getLong(rows[0])));
    } finally {
      operationIds.close();
    }
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
    int[] rows = instance.machines.byAvailableTime().rowIndexes();
    LongColumnView machineIds = instance.machines.machineIdValueColumn();
    LongColumnView available = instance.machines.availableFromMinuteColumn();
    LongColumnView lastFamily = instance.machines.lastSetupFamilyValueColumn();
    try {
      for (int row : rows) {
        MachineId machineId = new MachineId(machineIds.getLong(row));
        if (instance.frontier.findByMachine(machineId).count() == 0L) continue;
        boolean present = lastFamily.isPresent(row);
        return new MachineSelection(machineId, available.getLong(row), present,
          present ? lastFamily.getLong(row) : 0L);
      }
    } finally {
      lastFamily.close();
      available.close();
      machineIds.close();
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
    RemoveResult removed = instance.frontier
      .findByOperation(candidate.operationKey).remove();
    require(removed.removed() > 0L,
      "commit must remove all candidates of the assigned operation");
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
    int[] successors = instance.definitions
      .findByJobSequence(jobId, nextSequence).rowIndexes();
    if (successors.length == 1) {
      OperationKey successor = operationAt(dispatch.jobId, nextSequence);
      instance.operationStates.mutate(successor)
        .setJobReadyMinute(dispatch.endMinute).commit();
      frontier.release(successor);
      return Completion.incomplete();
    }
    require(successors.length == 0,
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
