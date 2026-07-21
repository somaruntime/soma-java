package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.MachineState;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyPair;
import com.hgtech.soma.examples.fjsp.schema.SetupTimeKey;
import com.hgtech.soma.examples.fjsp.schema.generated.CandidateMachineDefinitionBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.JobDefinitionBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.JobRuntimeStateBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationRuntimeStateBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.SetupTimeBatch;
import com.hgtech.soma.runtime.RuntimePlan;

/** 把输入模型一次性导入一个有效、尚未开始求解的 {@link FjspInstance}。 */
public final class FjspInstanceFactory {
  private static final int IMPORT_BATCH_SIZE = 512;

  private FjspInstanceFactory() {
  }

  public static FjspInstance create(FjspProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    RuntimePlan defaults = OperationDefinitionTable.defaultRuntimePlan();
    RuntimePlan plan = defaults.toBuilder()
      .maximumOwnershipTableInstances(problem.operationCount() + 1024L)
      .maximumAggregateStorageBytes(Math.max(
        defaults.maximumAggregateStorageBytes(), 2L * 1024L * 1024L * 1024L))
      .build();
    FjspInstance instance = new FjspInstance(
      problem.operationCount(), problem.jobCount(),
      problem.maximumCandidatesPerOperation, plan);
    boolean complete = false;
    try {
      reserve(problem, instance);
      importJobs(problem, instance);
      importMachines(problem, instance);
      importOperations(problem, instance);
      complete = true;
      return instance;
    } finally {
      if (!complete) instance.close();
    }
  }

  private static void reserve(FjspProblem problem, FjspInstance instance) {
    instance.jobs.reserve(problem.jobCount());
    instance.jobStates.reserve(problem.jobCount());
    instance.jobResults.reserve(problem.jobCount());
    instance.definitions.reserve(problem.operationCount());
    instance.operationStates.reserve(problem.operationCount());
    instance.assignments.reserve(problem.operationCount());
    instance.machines.reserve(problem.machineCount());
    instance.setupTimes.reserve(problem.setupTimes.size());
    instance.frontier.reserve(problem.frontierCapacity);
  }

  private static void importJobs(FjspProblem problem, FjspInstance instance) {
    for (int start = 0; start < problem.jobs.size(); start += IMPORT_BATCH_SIZE) {
      int count = Math.min(IMPORT_BATCH_SIZE, problem.jobs.size() - start);
      JobDefinitionBatch definitions = new JobDefinitionBatch(count);
      JobRuntimeStateBatch states = new JobRuntimeStateBatch(count);
      for (int offset = 0; offset < count; offset++) {
        FjspProblem.JobInput input = problem.jobs.get(start + offset);
        JobId job = new JobId(input.jobId);
        definitions.addValues(job, input.inputOrder, input.dueMinute,
          input.operationCount);
        states.addValues(job, 0);
      }
      instance.jobs.addBatch(definitions);
      instance.jobStates.addBatch(states);
    }
  }

  private static void importMachines(
      FjspProblem problem, FjspInstance instance) {
    MachineBatch machines = new MachineBatch(problem.machineCount());
    for (FjspProblem.MachineInput input : problem.machines) {
      boolean present = input.lastSetupFamily != null;
      machines.addValues(new MachineId(input.machineId), MachineState.READY,
        input.availableFromMinute, present,
        present ? new SetupFamilyId(input.lastSetupFamily.longValue()) : null);
    }
    instance.machines.addBatch(machines);

    SetupTimeBatch setupTimes = new SetupTimeBatch(problem.setupTimes.size());
    for (FjspProblem.SetupTimeInput input : problem.setupTimes) {
      setupTimes.addValues(new SetupTimeKey(new MachineId(input.machineId),
        new SetupFamilyPair(new SetupFamilyId(input.fromFamily),
          new SetupFamilyId(input.toFamily))), input.setupMinutes);
    }
    instance.setupTimes.addBatch(setupTimes);
  }

  private static void importOperations(
      FjspProblem problem, FjspInstance instance) {
    for (int start = 0; start < problem.operations.size();
         start += IMPORT_BATCH_SIZE) {
      int count = Math.min(
        IMPORT_BATCH_SIZE, problem.operations.size() - start);
      OperationDefinitionBatch definitions =
        new OperationDefinitionBatch(count);
      OperationRuntimeStateBatch states =
        new OperationRuntimeStateBatch(count);
      for (int offset = 0; offset < count; offset++) {
        FjspProblem.OperationInput input =
          problem.operations.get(start + offset);
        OperationKey key = new OperationKey(
          new JobId(input.jobId), new OperationId(input.operationId));
        CandidateMachineDefinitionBatch candidates =
          new CandidateMachineDefinitionBatch(input.machineIds.length);
        for (int candidate = 0; candidate < input.machineIds.length;
             candidate++) {
          candidates.addValues(new MachineId(input.machineIds[candidate]),
            input.processingMinutes[candidate]);
        }
        definitions.addValues(key, input.sequenceNo, input.releaseMinute,
          new SetupFamilyId(input.setupFamily), candidates);
        states.addValues(key, input.jobReadyMinute, input.materialReadyMinute);
      }
      instance.definitions.addBatch(definitions);
      instance.operationStates.addBatch(states);
    }
  }
}
