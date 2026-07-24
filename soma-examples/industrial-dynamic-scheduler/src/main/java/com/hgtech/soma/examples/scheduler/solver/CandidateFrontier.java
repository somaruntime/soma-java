package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.schema.DispatchCandidateKey;
import com.hgtech.soma.examples.scheduler.schema.JobId;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.OperationId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.examples.scheduler.schema.OperationStatus;
import com.hgtech.soma.examples.scheduler.schema.ResourceId;
import com.hgtech.soma.examples.scheduler.schema.SetupFamilyId;
import com.hgtech.soma.examples.scheduler.schema.generated.DispatchCandidateBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.DispatchCandidateCursor;
import com.hgtech.soma.examples.scheduler.schema.generated.DispatchCandidateScan;
import com.hgtech.soma.examples.scheduler.schema.generated.EligibleMachineTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationRuntimeStateMutator;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.UpdateResult;

/** Candidate 发布、刷新、排序选择与版本复验的唯一 Owner。 */
final class CandidateFrontier {
  private static final DispatchCandidateScan.Comparator TOTAL_ORDER =
      new DispatchCandidateScan.Comparator() {
        @Override
        public int compare(DispatchCandidateCursor left,
                           DispatchCandidateCursor right) {
          int result = Long.compare(setupStart(left), setupStart(right));
          if (result != 0) return result;
          result = Long.compare(
              left.completionMinute(), right.completionMinute());
          if (result != 0) return result;
          result = Integer.compare(right.priority(), left.priority());
          if (result != 0) return result;
          result = Long.compare(left.dueMinute(), right.dueMinute());
          if (result != 0) return result;
          result = Long.compare(
              left.candidateKeyOperationKeyJobIdValue(),
              right.candidateKeyOperationKeyJobIdValue());
          if (result != 0) return result;
          result = Long.compare(
              left.candidateKeyOperationKeyOperationIdValue(),
              right.candidateKeyOperationKeyOperationIdValue());
          if (result != 0) return result;
          return Long.compare(left.candidateKeyMachineIdValue(),
              right.candidateKeyMachineIdValue());
        }

        private long setupStart(DispatchCandidateCursor value) {
          return Math.subtractExact(
              value.effectiveStartMinute(), value.setupMinutes());
        }
      };

  private final SchedulerRuntime runtime;
  private final DispatchCandidateBatch releaseBatch;
  private final SelectedCandidate selected = new SelectedCandidate();

  CandidateFrontier(SchedulerRuntime runtime) {
    this.runtime = runtime;
    releaseBatch = new DispatchCandidateBatch(
        runtime.maximumCandidatesPerOperation());
  }

  boolean isEmpty() {
    return runtime.frontierSize() == 0;
  }

  SelectedCandidate refreshAndSelect() {
    refresh();
    selected.clear();
    runtime.frontier()
        .filter(candidate -> candidate.ready())
        .sorted(TOTAL_ORDER)
        .limit(1)
        .forEach(selected::copy);
    require(selected.present,
        "non-empty frontier has no ready candidate");
    return selected;
  }

  void revalidate(SelectedCandidate value) {
    require(runtime.frontier().containsKey(value.key()),
        "selected candidate was relocated or retired");
    int operationIndex =
        runtime.operationStates().requireIndex(value.operation());
    int machineIndex =
        runtime.machineStates().requireIndex(value.machine());
    int resourceIndex =
        runtime.resourceStates().requireIndex(value.resource());
    LongColumnView operationVersions =
        runtime.operationStates().versionColumn();
    LongColumnView machineVersions =
        runtime.machineStates().versionColumn();
    LongColumnView resourceVersions =
        runtime.resourceStates().versionColumn();
    try {
      require(operationVersions.getLong(operationIndex)
              == value.operationVersion,
          "stale operation candidate");
      require(machineVersions.getLong(machineIndex)
              == value.machineVersion,
          "stale machine candidate");
      require(resourceVersions.getLong(resourceIndex)
              == value.resourceVersion,
          "stale resource candidate");
    } finally {
      resourceVersions.close();
      machineVersions.close();
      operationVersions.close();
    }
  }

  void releaseOperation(
      OperationKey operation,
      long predecessorEnd,
      MachineId predecessorMachine) {
    int stateIndex =
        runtime.operationStates().requireIndex(operation);
    EnumColumnView<OperationStatus> statuses =
        runtime.operationStates().statusColumn();
    LongColumnView versions =
        runtime.operationStates().versionColumn();
    OperationStatus status;
    long previousVersion;
    try {
      status = statuses.get(stateIndex);
      previousVersion = versions.getLong(stateIndex);
    } finally {
      versions.close();
      statuses.close();
    }
    require(status == OperationStatus.WAITING,
        "only a waiting operation can enter the frontier");
    long operationVersion = Math.addExact(previousVersion, 1L);
    OperationRuntimeStateMutator stateMutation =
        runtime.operationStates().mutate(operation)
            .setStatus(OperationStatus.RELEASED)
            .setPredecessorEndMinute(predecessorEnd)
            .setVersion(operationVersion);
    if (predecessorMachine == null) {
      stateMutation.clearPredecessorMachine();
    } else {
      stateMutation.setPredecessorMachine(predecessorMachine);
    }
    stateMutation.commit();

    int definitionIndex =
        runtime.operationDefinitions().requireIndex(operation);
    LongColumnView families =
        runtime.operationDefinitions().setupFamilyValueColumn();
    LongColumnView resourceIds =
        runtime.operationDefinitions().requiredResourceValueColumn();
    IntColumnView resourceUnits =
        runtime.operationDefinitions().requiredResourceUnitsColumn();
    long family;
    long resource;
    int units;
    try {
      family = families.getLong(definitionIndex);
      resource = resourceIds.getLong(definitionIndex);
      units = resourceUnits.getInt(definitionIndex);
    } finally {
      resourceUnits.close();
      resourceIds.close();
      families.close();
    }

    int jobIndex = runtime.jobs().requireIndex(operation.jobId);
    LongColumnView releaseMinutes =
        runtime.jobs().releaseMinuteColumn();
    LongColumnView materialMinutes =
        runtime.jobs().materialReadyMinuteColumn();
    LongColumnView dueMinutes = runtime.jobs().dueMinuteColumn();
    IntColumnView priorities = runtime.jobs().priorityColumn();
    long jobReady;
    long due;
    int priority;
    try {
      jobReady = Math.max(releaseMinutes.getLong(jobIndex),
          materialMinutes.getLong(jobIndex));
      due = dueMinutes.getLong(jobIndex);
      priority = priorities.getInt(jobIndex);
    } finally {
      priorities.close();
      dueMinutes.close();
      materialMinutes.close();
      releaseMinutes.close();
    }

    EligibleMachineTable eligible =
        runtime.operationDefinitions().eligibleMachines(operation);
    LongColumnView machineIds = eligible.machineIdValueColumn();
    LongColumnView processingMinutes =
        eligible.processingMinutesColumn();
    releaseBatch.clear();
    try {
      for (int index = 0; index < eligible.size(); index++) {
        MachineId machine =
            new MachineId(machineIds.getLong(index));
        long transport = predecessorMachine == null ? 0L
            : transportMinutes(predecessorMachine, machine);
        long predecessorReady = Math.addExact(
            predecessorEnd, transport);
        long baseReady = Math.max(jobReady, predecessorReady);
        releaseBatch.addValues(
            new DispatchCandidateKey(operation, machine),
            new SetupFamilyId(family), new ResourceId(resource), units,
            baseReady, processingMinutes.getLong(index), 0L, transport,
            baseReady, baseReady, due, priority, operationVersion,
            0L, 0L, false);
      }
    } finally {
      processingMinutes.close();
      machineIds.close();
    }
    runtime.frontier().addBatch(releaseBatch);
  }

  OperationKey operationAt(long jobId, int sequence) {
    int index = runtime.operationDefinitions()
        .requireIndexByJobSequence(new JobId(jobId), sequence);
    LongColumnView operationIds = runtime.operationDefinitions()
        .operationKeyOperationIdValueColumn();
    try {
      return new OperationKey(new JobId(jobId),
          new OperationId(operationIds.getLong(index)));
    } finally {
      operationIds.close();
    }
  }

  private long transportMinutes(MachineId from, MachineId to) {
    int index = runtime.transportTimes()
        .requireIndex(from.value, to.value);
    LongColumnView minutes =
        runtime.transportTimes().transportMinutesColumn();
    try {
      return minutes.getLong(index);
    } finally {
      minutes.close();
    }
  }

  private void refresh() {
    final LongColumnView machineAvailable =
        runtime.machineStates().nextAvailableMinuteColumn();
    final LongColumnView machineFamilies =
        runtime.machineStates().lastSetupFamilyValueColumn();
    final LongColumnView machineVersions =
        runtime.machineStates().versionColumn();
    final LongColumnView operationVersions =
        runtime.operationStates().versionColumn();
    final LongColumnView resourceVersions =
        runtime.resourceStates().versionColumn();
    final LongColumnView setupValues =
        runtime.setupTimes().setupMinutesColumn();
    try {
      UpdateResult result = runtime.frontier().update(candidate -> {
        long machineId = candidate.candidateKeyMachineIdValue();
        long resourceId = candidate.requiredResourceValue();
        long jobId =
            candidate.candidateKeyOperationKeyJobIdValue();
        long operationId =
            candidate.candidateKeyOperationKeyOperationIdValue();
        int machineIndex =
            runtime.machineStates().requireIndex(machineId);
        int operationIndex =
            runtime.operationStates().requireIndex(jobId, operationId);
        int resourceIndex =
            runtime.resourceStates().requireIndex(resourceId);
        long setup = 0L;
        if (machineFamilies.isPresent(machineIndex)) {
          int setupIndex = runtime.setupTimes().requireIndex(
              machineId,
              machineFamilies.getLong(machineIndex),
              candidate.targetSetupFamilyValue());
          setup = setupValues.getLong(setupIndex);
        }
        long resourceReady = runtime.earliestResourceStart(
            resourceIndex, candidate.requiredResourceUnits());
        long earliestSetup = Math.max(candidate.baseReadyMinute(),
            machineAvailable.getLong(machineIndex));
        earliestSetup = Math.max(earliestSetup,
            Math.max(0L, Math.subtractExact(resourceReady, setup)));
        long occupied = Math.addExact(
            setup, candidate.processingMinutes());
        long setupStart = runtime.fitMachineInterval(
            machineIndex, earliestSetup, occupied);
        long processingStart = Math.addExact(setupStart, setup);
        long completion = Math.addExact(
            processingStart, candidate.processingMinutes());
        candidate.setSetupMinutes(setup);
        candidate.setEffectiveStartMinute(processingStart);
        candidate.setCompletionMinute(completion);
        candidate.setOperationVersion(
            operationVersions.getLong(operationIndex));
        candidate.setMachineVersion(
            machineVersions.getLong(machineIndex));
        candidate.setResourceVersion(
            resourceVersions.getLong(resourceIndex));
        candidate.setReady(true);
      });
      require(result.matched() == runtime.frontierSize(),
          "frontier refresh cardinality mismatch");
    } finally {
      setupValues.close();
      resourceVersions.close();
      operationVersions.close();
      machineVersions.close();
      machineFamilies.close();
      machineAvailable.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
