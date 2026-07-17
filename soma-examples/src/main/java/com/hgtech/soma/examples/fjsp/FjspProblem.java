package com.hgtech.soma.examples.fjsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与 SOMA runtime 解耦的 FJSP 输入模型。
 *
 * <p>对象列表只存在于导入边界；求解开始后，{@link FjspInstance} 中的 SOMA tables
 * 是运行期唯一事实源。</p>
 */
public final class FjspProblem {
  final List<JobInput> jobs;
  final List<MachineInput> machines;
  final List<SetupTimeInput> setupTimes;
  final List<OperationInput> operations;
  final int candidateCount;
  final int frontierCapacity;

  private FjspProblem(Builder builder) {
    jobs = immutableCopy(builder.jobs);
    machines = immutableCopy(builder.machines);
    setupTimes = immutableCopy(builder.setupTimes);
    operations = immutableCopy(builder.operations);
    candidateCount = builder.candidateCount;
    frontierCapacity = validateAndCalculateFrontierCapacity();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** 为阅读者保留 setup、可选字段和不同加工时间的最小完整问题。 */
  public static FjspProblem teachingExample() {
    return builder()
      .addJob(1L, 0L, 50L, 2)
      .addMachine(100L)
      .addMachineWithLastSetupFamily(200L, 100L, 8L)
      .addSetupTime(100L, 7L, 8L, 2L)
      .addSetupTime(200L, 8L, 7L, 0L)
      .addOperation(1L, 10L, 0, 0L, 7L, 0L, 0L,
        new long[]{100L, 200L}, new long[]{6L, 4L})
      .addOperation(1L, 20L, 1, 0L, 8L, 0L, 0L,
        new long[]{100L}, new long[]{3L})
      .build();
  }

  public int jobCount() {
    return jobs.size();
  }

  public int machineCount() {
    return machines.size();
  }

  public int operationCount() {
    return operations.size();
  }

  public int candidateCount() {
    return candidateCount;
  }

  private int validateAndCalculateFrontierCapacity() {
    if (jobs.isEmpty() || machines.isEmpty() || operations.isEmpty()) {
      throw new IllegalArgumentException(
        "FJSP problem requires jobs, machines and operations");
    }
    int expectedOperations = 0;
    Map<Long, JobInput> jobsById = new HashMap<Long, JobInput>();
    for (JobInput job : jobs) {
      expectedOperations = Math.addExact(expectedOperations, job.operationCount);
      if (jobsById.put(Long.valueOf(job.jobId), job) != null) {
        throw new IllegalArgumentException("duplicate job id: " + job.jobId);
      }
    }
    if (expectedOperations != operations.size()) {
      throw new IllegalArgumentException(
        "job operation counts do not match operation inputs");
    }
    Set<Long> machineIds = new HashSet<Long>();
    for (MachineInput machine : machines) {
      if (!machineIds.add(Long.valueOf(machine.machineId))) {
        throw new IllegalArgumentException(
          "duplicate machine id: " + machine.machineId);
      }
    }
    Set<String> operationIdentities = new HashSet<String>();
    Set<String> jobSequences = new HashSet<String>();
    Map<Long, Integer> maximumCandidatesByJob =
      new HashMap<Long, Integer>();
    for (OperationInput operation : operations) {
      JobInput job = jobsById.get(Long.valueOf(operation.jobId));
      if (job == null || operation.sequenceNo < 0
          || operation.sequenceNo >= job.operationCount) {
        throw new IllegalArgumentException("invalid operation job/sequence");
      }
      if (!operationIdentities.add(
          operation.jobId + ":" + operation.operationId)
          || !jobSequences.add(operation.jobId + ":" + operation.sequenceNo)) {
        throw new IllegalArgumentException("duplicate operation identity/sequence");
      }
      for (int index = 0; index < operation.machineIds.length; index++) {
        if (!machineIds.contains(Long.valueOf(operation.machineIds[index]))
            || operation.processingMinutes[index] <= 0L) {
          throw new IllegalArgumentException(
            "invalid candidate machine or processing time");
        }
      }
      Integer current = maximumCandidatesByJob.get(
        Long.valueOf(operation.jobId));
      if (current == null || operation.machineIds.length > current.intValue()) {
        maximumCandidatesByJob.put(Long.valueOf(operation.jobId),
          Integer.valueOf(operation.machineIds.length));
      }
    }
    int capacity = 0;
    for (Integer maximum : maximumCandidatesByJob.values()) {
      capacity = Math.addExact(capacity, maximum.intValue());
    }
    return capacity;
  }

  private static <T> List<T> immutableCopy(List<T> values) {
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }

  static final class JobInput {
    final long jobId;
    final long inputOrder;
    final long dueMinute;
    final int operationCount;

    JobInput(long jobId, long inputOrder, long dueMinute, int operationCount) {
      this.jobId = jobId;
      this.inputOrder = inputOrder;
      this.dueMinute = dueMinute;
      this.operationCount = operationCount;
    }
  }

  static final class MachineInput {
    final long machineId;
    final long availableFromMinute;
    final Long lastSetupFamily;

    MachineInput(long machineId, long availableFromMinute,
                 Long lastSetupFamily) {
      this.machineId = machineId;
      this.availableFromMinute = availableFromMinute;
      this.lastSetupFamily = lastSetupFamily;
    }
  }

  static final class SetupTimeInput {
    final long machineId;
    final long fromFamily;
    final long toFamily;
    final long setupMinutes;

    SetupTimeInput(long machineId, long fromFamily, long toFamily,
                   long setupMinutes) {
      this.machineId = machineId;
      this.fromFamily = fromFamily;
      this.toFamily = toFamily;
      this.setupMinutes = setupMinutes;
    }
  }

  static final class OperationInput {
    final long jobId;
    final long operationId;
    final int sequenceNo;
    final long releaseMinute;
    final long setupFamily;
    final long jobReadyMinute;
    final long materialReadyMinute;
    final long[] machineIds;
    final long[] processingMinutes;

    OperationInput(long jobId, long operationId, int sequenceNo,
                   long releaseMinute, long setupFamily, long jobReadyMinute,
                   long materialReadyMinute, long[] machineIds,
                   long[] processingMinutes) {
      this.jobId = jobId;
      this.operationId = operationId;
      this.sequenceNo = sequenceNo;
      this.releaseMinute = releaseMinute;
      this.setupFamily = setupFamily;
      this.jobReadyMinute = jobReadyMinute;
      this.materialReadyMinute = materialReadyMinute;
      this.machineIds = machineIds.clone();
      this.processingMinutes = processingMinutes.clone();
    }
  }

  public static final class Builder {
    private final List<JobInput> jobs = new ArrayList<JobInput>();
    private final List<MachineInput> machines = new ArrayList<MachineInput>();
    private final List<SetupTimeInput> setupTimes =
      new ArrayList<SetupTimeInput>();
    private final List<OperationInput> operations =
      new ArrayList<OperationInput>();
    private int candidateCount;

    public Builder addJob(long jobId, long inputOrder, long dueMinute,
                          int operationCount) {
      if (operationCount <= 0) {
        throw new IllegalArgumentException("operationCount must be positive");
      }
      jobs.add(new JobInput(jobId, inputOrder, dueMinute, operationCount));
      return this;
    }

    public Builder addMachine(long machineId) {
      return addMachine(machineId, 0L, null);
    }

    public Builder addMachine(long machineId, long availableFromMinute,
                              Long lastSetupFamily) {
      machines.add(new MachineInput(
        machineId, availableFromMinute, lastSetupFamily));
      return this;
    }

    public Builder addMachineWithLastSetupFamily(
        long machineId, long availableFromMinute, long lastSetupFamily) {
      return addMachine(machineId, availableFromMinute,
        Long.valueOf(lastSetupFamily));
    }

    public Builder addSetupTime(long machineId, long fromFamily,
                                long toFamily, long setupMinutes) {
      setupTimes.add(new SetupTimeInput(
        machineId, fromFamily, toFamily, setupMinutes));
      return this;
    }

    public Builder addOperation(long jobId, long operationId, int sequenceNo,
                                long releaseMinute, long setupFamily,
                                long jobReadyMinute, long materialReadyMinute,
                                long[] machineIds,
                                long[] processingMinutes) {
      if (machineIds == null || processingMinutes == null
          || machineIds.length == 0
          || machineIds.length != processingMinutes.length) {
        throw new IllegalArgumentException(
          "candidate machine and processing-time arrays must align");
      }
      operations.add(new OperationInput(jobId, operationId, sequenceNo,
        releaseMinute, setupFamily, jobReadyMinute, materialReadyMinute,
        machineIds, processingMinutes));
      candidateCount = Math.addExact(candidateCount, machineIds.length);
      return this;
    }

    public FjspProblem build() {
      return new FjspProblem(this);
    }
  }
}
