package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.state.DispatchCandidateKey;
import com.hgtech.soma.examples.scheduler.state.JobId;
import com.hgtech.soma.examples.scheduler.state.MachineId;
import com.hgtech.soma.examples.scheduler.state.OperationId;
import com.hgtech.soma.examples.scheduler.state.OperationKey;
import com.hgtech.soma.examples.scheduler.state.OperationStatus;
import com.hgtech.soma.examples.scheduler.state.ResourceId;
import com.hgtech.soma.examples.scheduler.state.SetupFamilyId;
import com.hgtech.soma.examples.scheduler.state.generated.DispatchCandidateBatch;
import com.hgtech.soma.examples.scheduler.state.generated.DispatchCandidateCursor;
import com.hgtech.soma.examples.scheduler.state.generated.DispatchCandidateScan;
import com.hgtech.soma.examples.scheduler.state.generated.EligibleMachineTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationAssignmentBatch;
import com.hgtech.soma.examples.scheduler.state.generated.OperationRuntimeStateMutator;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.RemoveResult;
import com.hgtech.soma.runtime.UpdateResult;

import java.util.HashSet;
import java.util.Set;

/**
 * application-owned event/dispatch/commit 核心。
 *
 * <p>SOMA V1 不提供跨 Table transaction；authoritative write 后失败会让本实例
 * fail-stop，由调用方关闭并从 detached input 重建。</p>
 */
public final class IndustrialScheduler {
  private static final DispatchCandidateScan.Comparator TOTAL_ORDER =
      new DispatchCandidateScan.Comparator() {
        @Override
        public int compare(DispatchCandidateCursor left,
                           DispatchCandidateCursor right) {
          int result = Long.compare(setupStart(left), setupStart(right));
          if (result != 0) return result;
          result = Long.compare(left.completionMinute(),
              right.completionMinute());
          if (result != 0) return result;
          result = Integer.compare(right.priority(), left.priority());
          if (result != 0) return result;
          result = Long.compare(left.dueMinute(), right.dueMinute());
          if (result != 0) return result;
          result = Long.compare(left.candidateKeyOperationKeyJobIdValue(),
              right.candidateKeyOperationKeyJobIdValue());
          if (result != 0) return result;
          result = Long.compare(left.candidateKeyOperationKeyOperationIdValue(),
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
  private final OperationAssignmentBatch assignmentBatch =
      new OperationAssignmentBatch(1);
  private final SelectedCandidate selected = new SelectedCandidate();
  private final Set<Long> affectedJobs = new HashSet<Long>();
  private long processedEvents;
  private long makespan;
  private long totalTardiness;
  private long weightedTardiness;
  private int completedJobs;
  private boolean solved;

  public IndustrialScheduler(SchedulerRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    this.runtime = runtime;
    releaseBatch = new DispatchCandidateBatch(
        runtime.maximumCandidatesPerOperation);
  }

  public ScheduleResult solve() {
    if (solved) throw new IllegalStateException("scheduler is one-shot");
    solved = true;
    while (runtime.assignments.size() < runtime.operationCount) {
      if (runtime.frontier.size() == 0) {
        require(!runtime.events.isEmpty(),
            "unscheduled operations remain without event or candidate");
        processEventsThrough(runtime.events.peek().minute);
        continue;
      }
      refreshFrontier();
      selectCandidate();
      long setupStart = Math.subtractExact(
          selected.effectiveStartMinute, selected.setupMinutes);
      if (!runtime.events.isEmpty()
          && runtime.events.peek().minute <= setupStart) {
        processEventsThrough(runtime.events.peek().minute);
        continue;
      }
      revalidateSelected();
      commitSelected(setupStart);
    }
    processEventsThrough(makespan);
    require(runtime.frontier.size() == 0,
        "frontier must be empty after all assignments");
    require(completedJobs == runtime.jobCount,
        "all jobs must be completed");
    return new ScheduleResult(runtime.assignments.size(), completedJobs,
        makespan, totalTardiness, weightedTardiness, processedEvents,
        resultChecksum());
  }

  private void processEventsThrough(long cutoff) {
    affectedJobs.clear();
    while (!runtime.events.isEmpty()
        && runtime.events.peek().minute <= cutoff) {
      ExternalEvent event = runtime.events.remove();
      processedEvents = Math.addExact(processedEvents, 1L);
      if (event.type == SchedulingProblem.JOB_RELEASE) {
        runtime.jobGates.get(Long.valueOf(event.subjectId)).released = true;
        affectedJobs.add(Long.valueOf(event.subjectId));
      } else if (event.type == SchedulingProblem.MATERIAL_READY) {
        runtime.jobGates.get(Long.valueOf(event.subjectId)).materialReady = true;
        affectedJobs.add(Long.valueOf(event.subjectId));
      } else if (event.type == SchedulingProblem.MACHINE_DELAY) {
        applyMachineDelay(event);
      } else {
        throw new IllegalStateException("unknown runtime event " + event.type);
      }
    }
    for (Long jobId : affectedJobs) {
      JobGate gate = runtime.jobGates.get(jobId);
      if (gate.readyToPublish()) {
        OperationKey first = operationAt(jobId.longValue(), 0);
        releaseOperation(first, 0L, null);
        gate.initialOperationPublished = true;
      }
    }
  }

  private void applyMachineDelay(ExternalEvent event) {
    MachineId machine = new MachineId(event.subjectId);
    int row = runtime.machineStates.requireIndex(machine);
    LongColumnView availability =
        runtime.machineStates.nextAvailableMinuteColumn();
    LongColumnView versions = runtime.machineStates.versionColumn();
    long currentAvailability;
    long version;
    try {
      currentAvailability = availability.getLong(row);
      version = versions.getLong(row);
    } finally {
      versions.close();
      availability.close();
    }
    runtime.machineStates.mutate(machine)
        .setNextAvailableMinute(Math.max(currentAvailability, event.value))
        .setVersion(Math.addExact(version, 1L))
        .commit();
  }

  private void releaseOperation(OperationKey operation,
                                long predecessorEnd,
                                MachineId predecessorMachine) {
    int stateRow = runtime.operationStates.requireIndex(operation);
    EnumColumnView<OperationStatus> statuses =
        runtime.operationStates.statusColumn();
    LongColumnView versions = runtime.operationStates.versionColumn();
    OperationStatus status;
    long previousVersion;
    try {
      status = statuses.get(stateRow);
      previousVersion = versions.getLong(stateRow);
    } finally {
      versions.close();
      statuses.close();
    }
    require(status == OperationStatus.WAITING,
        "only a waiting operation can enter the frontier");
    long operationVersion = Math.addExact(previousVersion, 1L);
    OperationRuntimeStateMutator stateMutation =
        runtime.operationStates.mutate(operation)
        .setStatus(OperationStatus.RELEASED)
        .setPredecessorEndMinute(predecessorEnd)
        .setVersion(operationVersion);
    if (predecessorMachine == null) {
      stateMutation.clearPredecessorMachine();
    } else {
      stateMutation.setPredecessorMachine(predecessorMachine);
    }
    stateMutation.commit();

    int definitionRow =
        runtime.operationDefinitions.requireIndex(operation);
    LongColumnView families =
        runtime.operationDefinitions.setupFamilyValueColumn();
    LongColumnView resourceIds =
        runtime.operationDefinitions.requiredResourceValueColumn();
    IntColumnView resourceUnits =
        runtime.operationDefinitions.requiredResourceUnitsColumn();
    long family;
    long resource;
    int units;
    try {
      family = families.getLong(definitionRow);
      resource = resourceIds.getLong(definitionRow);
      units = resourceUnits.getInt(definitionRow);
    } finally {
      resourceUnits.close();
      resourceIds.close();
      families.close();
    }

    int jobRow = runtime.jobs.requireIndex(operation.jobId);
    LongColumnView releaseMinutes = runtime.jobs.releaseMinuteColumn();
    LongColumnView materialMinutes =
        runtime.jobs.materialReadyMinuteColumn();
    LongColumnView dueMinutes = runtime.jobs.dueMinuteColumn();
    IntColumnView priorities = runtime.jobs.priorityColumn();
    long jobReady;
    long due;
    int priority;
    try {
      jobReady = Math.max(releaseMinutes.getLong(jobRow),
          materialMinutes.getLong(jobRow));
      due = dueMinutes.getLong(jobRow);
      priority = priorities.getInt(jobRow);
    } finally {
      priorities.close();
      dueMinutes.close();
      materialMinutes.close();
      releaseMinutes.close();
    }

    EligibleMachineTable eligible =
        runtime.operationDefinitions.eligibleMachines(operation);
    LongColumnView machineIds = eligible.machineIdValueColumn();
    LongColumnView processingMinutes = eligible.processingMinutesColumn();
    releaseBatch.clear();
    try {
      for (int row = 0; row < eligible.size(); row++) {
        MachineId machine = new MachineId(machineIds.getLong(row));
        long transport = predecessorMachine == null ? 0L
            : transportMinutes(predecessorMachine, machine);
        long predecessorReady = Math.addExact(predecessorEnd, transport);
        long baseReady = Math.max(jobReady, predecessorReady);
        releaseBatch.addValues(
            new DispatchCandidateKey(operation, machine),
            new SetupFamilyId(family), new ResourceId(resource), units,
            baseReady, processingMinutes.getLong(row), 0L, transport,
            baseReady, baseReady, due, priority, operationVersion,
            0L, 0L, false);
      }
    } finally {
      processingMinutes.close();
      machineIds.close();
    }
    runtime.frontier.addBatch(releaseBatch);
  }

  private long transportMinutes(MachineId from, MachineId to) {
    int row = runtime.transportTimes.requireIndex(from.value, to.value);
    LongColumnView minutes = runtime.transportTimes.transportMinutesColumn();
    try {
      return minutes.getLong(row);
    } finally {
      minutes.close();
    }
  }

  private void refreshFrontier() {
    final LongColumnView machineAvailable =
        runtime.machineStates.nextAvailableMinuteColumn();
    final LongColumnView machineFamilies =
        runtime.machineStates.lastSetupFamilyValueColumn();
    final LongColumnView machineVersions =
        runtime.machineStates.versionColumn();
    final LongColumnView operationVersions =
        runtime.operationStates.versionColumn();
    final LongColumnView resourceVersions =
        runtime.resourceStates.versionColumn();
    final LongColumnView setupValues =
        runtime.setupTimes.setupMinutesColumn();
    try {
      UpdateResult result = runtime.frontier.update(candidate -> {
        MachineId machine =
            new MachineId(candidate.candidateKeyMachineIdValue());
        ResourceId resource =
            new ResourceId(candidate.requiredResourceValue());
        OperationKey operation = new OperationKey(
            new JobId(candidate.candidateKeyOperationKeyJobIdValue()),
            new OperationId(
                candidate.candidateKeyOperationKeyOperationIdValue()));
        int machineRow = runtime.machineStates.requireIndex(machine);
        int operationRow = runtime.operationStates.requireIndex(operation);
        int resourceRow = runtime.resourceStates.requireIndex(resource);
        long setup = 0L;
        if (machineFamilies.isPresent(machineRow)) {
          int setupRow = runtime.setupTimes.requireIndex(machine.value,
              machineFamilies.getLong(machineRow),
              candidate.targetSetupFamilyValue());
          setup = setupValues.getLong(setupRow);
        }
        ResourceCalendar resourceCalendar =
            runtime.resourceCalendars.get(Long.valueOf(resource.value));
        long resourceReady =
            resourceCalendar.earliestStart(candidate.requiredResourceUnits());
        long earliestSetup = Math.max(candidate.baseReadyMinute(),
            machineAvailable.getLong(machineRow));
        earliestSetup = Math.max(earliestSetup,
            Math.max(0L, Math.subtractExact(resourceReady, setup)));
        long occupied = Math.addExact(setup, candidate.processingMinutes());
        long setupStart = MachineCalendar.fit(
            runtime, machine, earliestSetup, occupied);
        long processingStart = Math.addExact(setupStart, setup);
        long completion = Math.addExact(
            processingStart, candidate.processingMinutes());
        candidate.setSetupMinutes(setup);
        candidate.setEffectiveStartMinute(processingStart);
        candidate.setCompletionMinute(completion);
        candidate.setOperationVersion(operationVersions.getLong(operationRow));
        candidate.setMachineVersion(machineVersions.getLong(machineRow));
        candidate.setResourceVersion(resourceVersions.getLong(resourceRow));
        candidate.setReady(true);
      });
      require(result.matched() == runtime.frontier.size(),
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

  private void selectCandidate() {
    selected.clear();
    runtime.frontier
        .filter(candidate -> candidate.ready())
        .sorted(TOTAL_ORDER)
        .limit(1)
        .forEach(selected::copy);
    require(selected.present, "non-empty frontier has no ready candidate");
  }

  private void revalidateSelected() {
    require(runtime.frontier.containsKey(selected.key()),
        "selected candidate was relocated or retired");
    int operationRow =
        runtime.operationStates.requireIndex(selected.operation());
    int machineRow = runtime.machineStates.requireIndex(selected.machine());
    int resourceRow =
        runtime.resourceStates.requireIndex(selected.resource());
    LongColumnView operationVersions =
        runtime.operationStates.versionColumn();
    LongColumnView machineVersions = runtime.machineStates.versionColumn();
    LongColumnView resourceVersions = runtime.resourceStates.versionColumn();
    try {
      require(operationVersions.getLong(operationRow)
              == selected.operationVersion,
          "stale operation candidate");
      require(machineVersions.getLong(machineRow) == selected.machineVersion,
          "stale machine candidate");
      require(resourceVersions.getLong(resourceRow)
              == selected.resourceVersion,
          "stale resource candidate");
    } finally {
      resourceVersions.close();
      machineVersions.close();
      operationVersions.close();
    }
  }

  private void commitSelected(long setupStart) {
    OperationKey operation = selected.operation();
    MachineId machine = selected.machine();
    ResourceId resource = selected.resource();

    assignmentBatch.clear();
    assignmentBatch.addValues(operation, machine, resource,
        new SetupFamilyId(selected.targetSetupFamily), setupStart,
        selected.setupMinutes, selected.transportMinutes,
        selected.effectiveStartMinute, selected.processingMinutes,
        selected.completionMinute, selected.dueMinute, selected.priority);
    runtime.assignments.addBatch(assignmentBatch);

    runtime.machineStates.mutate(machine)
        .setNextAvailableMinute(selected.completionMinute)
        .setLastSetupFamily(new SetupFamilyId(selected.targetSetupFamily))
        .setVersion(Math.addExact(selected.machineVersion, 1L))
        .commit();

    ResourceCalendar resourceCalendar =
        runtime.resourceCalendars.get(Long.valueOf(selected.resourceId));
    resourceCalendar.commit(selected.effectiveStartMinute,
        selected.completionMinute, selected.resourceUnits);
    runtime.resourceStates.mutate(resource)
        .setNextAvailableMinute(resourceCalendar.nextAvailableMinute())
        .setVersion(Math.addExact(selected.resourceVersion, 1L))
        .commit();

    runtime.operationStates.mutate(operation)
        .setStatus(OperationStatus.SCHEDULED)
        .setVersion(Math.addExact(selected.operationVersion, 1L))
        .commit();

    RemoveResult removed = runtime.frontier.scanByOperation(operation).remove();
    require(removed.removed() > 0L,
        "assignment must retire every candidate of its operation");
    releaseSuccessor(operation, machine, selected.completionMinute);

    makespan = Math.max(makespan, selected.completionMinute);
  }

  private void releaseSuccessor(OperationKey operation, MachineId machine,
                                long predecessorEnd) {
    int definitionRow =
        runtime.operationDefinitions.requireIndex(operation);
    IntColumnView sequences =
        runtime.operationDefinitions.sequenceNoColumn();
    int sequence;
    try {
      sequence = sequences.getInt(definitionRow);
    } finally {
      sequences.close();
    }
    int next = Math.addExact(sequence, 1);
    int successorRow = runtime.operationDefinitions.findIndexByJobSequence(
        operation.jobId, next);
    if (successorRow >= 0) {
      LongColumnView operationIds =
          runtime.operationDefinitions.operationKeyOperationIdValueColumn();
      long successorId;
      try {
        successorId = operationIds.getLong(successorRow);
      } finally {
        operationIds.close();
      }
      releaseOperation(new OperationKey(operation.jobId,
          new OperationId(successorId)), predecessorEnd, machine);
      return;
    }
    long tardiness = Math.max(0L,
        Math.subtractExact(selected.completionMinute, selected.dueMinute));
    totalTardiness = Math.addExact(totalTardiness, tardiness);
    weightedTardiness = Math.addExact(weightedTardiness,
        Math.multiplyExact(tardiness, (long) selected.priority));
    completedJobs = Math.addExact(completedJobs, 1);
  }

  private OperationKey operationAt(long jobId, int sequence) {
    int row = runtime.operationDefinitions.requireIndexByJobSequence(
        new JobId(jobId), sequence);
    LongColumnView operationIds =
        runtime.operationDefinitions.operationKeyOperationIdValueColumn();
    try {
      return new OperationKey(new JobId(jobId),
          new OperationId(operationIds.getLong(row)));
    } finally {
      operationIds.close();
    }
  }

  private String resultChecksum() {
    return com.hgtech.soma.examples.scheduler.validation.ScheduleValidator
        .assignmentChecksum(runtime.exportAssignments());
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class SelectedCandidate {
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
      return new OperationKey(new JobId(jobId), new OperationId(operationId));
    }

    MachineId machine() {
      return new MachineId(machineId);
    }

    ResourceId resource() {
      return new ResourceId(resourceId);
    }
  }
}
