package io.github.somaruntime.soma.examples.scheduler.solver;

import io.github.somaruntime.soma.examples.scheduler.runtime.SchedulerRuntime;
import io.github.somaruntime.soma.examples.scheduler.schema.JobId;
import io.github.somaruntime.soma.examples.scheduler.schema.MachineId;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationId;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationKey;
import io.github.somaruntime.soma.examples.scheduler.schema.OperationStatus;
import io.github.somaruntime.soma.examples.scheduler.schema.ResourceId;
import io.github.somaruntime.soma.examples.scheduler.schema.generated.EligibleMachineCursor;
import io.github.somaruntime.soma.examples.scheduler.schema.generated.EligibleMachineScan;
import io.github.somaruntime.soma.examples.scheduler.schema.generated.OperationRuntimeStateMutator;
import io.github.somaruntime.soma.runtime.EnumColumnView;
import io.github.somaruntime.soma.runtime.IntColumnView;
import io.github.somaruntime.soma.runtime.LongColumnView;

/**
 * Candidate 发布、增量刷新、全局选择与版本复验的唯一 Owner。
 *
 * <p>SOMA tables 保存定义和 authoritative runtime state。candidate 是可由
 * 这些事实重建的短命求解投影，由 primitive pool 和每机代表项最小堆持有，
 * 不进入 SOMA table graph。</p>
 */
final class CandidateFrontier implements AutoCloseable {
  private final SchedulerRuntime runtime;
  private final CandidatePool candidates;
  private final MachineFrontierHeap heap;
  private final SelectedCandidate selected = new SelectedCandidate();
  private final SelectedCandidate machineBest =
      new SelectedCandidate();
  private final SelectedCandidate releasedCandidate =
      new SelectedCandidate();
  private final SelectedCandidate candidateScratch =
      new SelectedCandidate();
  private final EligibleMachineAccess eligibleMachineAccess =
      new EligibleMachineAccess();
  private final ResourceCalendar[] resourceCalendars;

  /*
   * 下列 view 绑定的 Table 在 solve 中只有 point mutation，没有结构变化。
   * 因此它们可以安全复用，并在 frontier lifecycle 结束时统一关闭。
   */
  private final EnumColumnView<OperationStatus> operationStatuses;
  private final LongColumnView operationVersions;
  private final LongColumnView definitionFamilies;
  private final LongColumnView definitionResourceIds;
  private final IntColumnView definitionResourceUnits;
  private final LongColumnView definitionOperationIds;
  private final IntColumnView definitionSequences;
  private final LongColumnView jobReleaseMinutes;
  private final LongColumnView jobMaterialMinutes;
  private final LongColumnView jobDueMinutes;
  private final IntColumnView jobPriorities;
  private final LongColumnView transportValues;
  private final LongColumnView machineAvailable;
  private final LongColumnView machineFamilies;
  private final LongColumnView machineVersions;
  private final LongColumnView resourceVersions;
  private final LongColumnView setupValues;
  private boolean closed;

  CandidateFrontier(SchedulerRuntime runtime) {
    this.runtime = runtime;
    candidates = new CandidatePool(
        runtime.frontierCapacity(), runtime.machineStates().size());
    heap = new MachineFrontierHeap(runtime.machineStates().size());
    operationStatuses = runtime.operationStates().statusColumn();
    operationVersions = runtime.operationStates().versionColumn();
    definitionFamilies =
        runtime.operationDefinitions().setupFamilyValueColumn();
    definitionResourceIds =
        runtime.operationDefinitions().requiredResourceValueColumn();
    definitionResourceUnits =
        runtime.operationDefinitions().requiredResourceUnitsColumn();
    definitionOperationIds =
        runtime.operationDefinitions()
            .operationKeyOperationIdValueColumn();
    definitionSequences =
        runtime.operationDefinitions().sequenceNoColumn();
    jobReleaseMinutes = runtime.jobs().releaseMinuteColumn();
    jobMaterialMinutes = runtime.jobs().materialReadyMinuteColumn();
    jobDueMinutes = runtime.jobs().dueMinuteColumn();
    jobPriorities = runtime.jobs().priorityColumn();
    transportValues = runtime.transportTimes().transportMinutesColumn();
    machineAvailable =
        runtime.machineStates().nextAvailableMinuteColumn();
    machineFamilies =
        runtime.machineStates().lastSetupFamilyValueColumn();
    machineVersions = runtime.machineStates().versionColumn();
    resourceVersions = runtime.resourceStates().versionColumn();
    setupValues = runtime.setupTimes().setupMinutesColumn();

    int resourceCount = runtime.resourceStates().size();
    resourceCalendars = new ResourceCalendar[resourceCount];
    IntColumnView capacities =
        runtime.resourceStates().capacityColumn();
    try {
      for (int resourceIndex = 0;
           resourceIndex < resourceCount; resourceIndex++) {
        resourceCalendars[resourceIndex] =
            new ResourceCalendar(
                capacities.getInt(resourceIndex));
      }
    } finally {
      capacities.close();
    }
  }

  boolean isEmpty() {
    if (candidates.size() == 0) {
      heap.clear();
      return true;
    }
    require(!heap.isEmpty(),
        "candidate pool has no machine representative");
    return false;
  }

  SelectedCandidate select() {
    require(candidates.size() > 0 && !heap.isEmpty(),
        "cannot select from an empty frontier");
    while (true) {
      int machineIndex = heap.bestMachineIndex();
      long machineId = heap.bestMachineId();
      if (heap.bestIsDirty()) {
        recomputeMachine(machineIndex, machineId);
        continue;
      }
      selected.clear();
      heap.copyBest(selected);
      int resourceIndex =
          runtime.resourceStates().requireIndex(selected.resourceId);
      long machineVersion = machineVersions.getLong(machineIndex);
      long resourceVersion = resourceVersions.getLong(resourceIndex);
      if (selected.machineVersion != machineVersion) {
        recomputeMachine(machineIndex, machineId);
        continue;
      }
      if (selected.resourceVersion == resourceVersion) {
        return selected;
      }
      if (advanceResourceVersionWithoutScoreChange(
          selected, resourceIndex, resourceVersion)) {
        return selected;
      }
      /*
       * Resource lane availability only moves forward, so a cached machine
       * representative remains a lower bound after resource mutation. Machine
       * mutation and candidate membership changes publish an explicit lower
       * bound marker. In both cases only the root machine must be recomputed.
       */
      recomputeMachine(machineIndex, machineId);
    }
  }

  void revalidate(SelectedCandidate value) {
    require(candidates.contains(
            value.jobId, value.operationId, value.machineId),
        "selected candidate was retired");
    int operationIndex =
        runtime.operationStates().requireIndex(value.operation());
    int machineIndex =
        runtime.machineStates().requireIndex(value.machineId);
    int resourceIndex =
        runtime.resourceStates().requireIndex(value.resourceId);
    require(operationVersions.getLong(operationIndex)
            == value.operationVersion,
        "stale operation candidate");
    require(machineVersions.getLong(machineIndex)
            == value.machineVersion,
        "stale machine candidate");
    require(resourceVersions.getLong(resourceIndex)
            == value.resourceVersion,
        "stale resource candidate");
  }

  void releaseOperation(
      OperationKey operation,
      long predecessorEnd,
      MachineId predecessorMachine) {
    int stateIndex =
        runtime.operationStates().requireIndex(operation);
    OperationStatus status = operationStatuses.get(stateIndex);
    long previousVersion = operationVersions.getLong(stateIndex);
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
    long family = definitionFamilies.getLong(definitionIndex);
    long resource = definitionResourceIds.getLong(definitionIndex);
    int units = definitionResourceUnits.getInt(definitionIndex);

    int jobIndex = runtime.jobs().requireIndex(operation.jobId);
    long jobReady = Math.max(
        jobReleaseMinutes.getLong(jobIndex),
        jobMaterialMinutes.getLong(jobIndex));
    long due = jobDueMinutes.getLong(jobIndex);
    int priority = jobPriorities.getInt(jobIndex);

    eligibleMachineAccess.publish(
        operation, predecessorEnd, predecessorMachine,
        family, resource, units, jobReady, due, priority,
        operationVersion);
  }

  void refreshMachine(MachineId machine) {
    heap.markDirty(runtime.machineStates().requireIndex(machine));
  }

  void commitResource(
      ResourceId resource,
      long startMinute,
      long endMinute,
      int units) {
    resourceCalendars[
        runtime.resourceStates().requireIndex(resource)]
        .commit(startMinute, endMinute, units);
  }

  void retireOperation(OperationKey operation) {
    int removed = eligibleMachineAccess.retire(operation);
    require(removed > 0,
        "assignment must retire every candidate of its operation");
  }

  OperationKey operationAt(long jobId, int sequence) {
    int index = runtime.operationDefinitions()
        .requireIndexByJobSequence(new JobId(jobId), sequence);
    return new OperationKey(new JobId(jobId),
        new OperationId(definitionOperationIds.getLong(index)));
  }

  int operationSequence(OperationKey operation) {
    return definitionSequences.getInt(
        runtime.operationDefinitions().requireIndex(operation));
  }

  long operationIdAt(long jobId, int sequence) {
    int index = runtime.operationDefinitions()
        .findIndexByJobSequence(new JobId(jobId), sequence);
    return index < 0 ? Long.MIN_VALUE
        : definitionOperationIds.getLong(index);
  }

  private long transportMinutes(long from, long to) {
    int index = runtime.transportTimes()
        .requireIndex(from, to);
    return transportValues.getLong(index);
  }

  private void recomputeMachine(
      int machineIndex, long machineId) {
    machineBest.clear();
    long machineVersion = machineVersions.getLong(machineIndex);
    long machineFamily = 0L;
    long machineAvailableMinute = 0L;
    boolean machineSnapshotLoaded = false;
    for (int slot = candidates.firstByMachine(machineIndex);
         slot >= 0; slot = candidates.nextByMachine(slot)) {
      candidateScratch.clear();
      candidates.copyTo(slot, candidateScratch);
      require(candidateScratch.machineId == machineId,
          "machine candidate group contains the wrong machine");
      int resourceIndex = candidates.resourceIndex(slot);
      long resourceVersion =
          resourceVersions.getLong(resourceIndex);
      boolean refreshed = false;
      if (candidateScratch.machineVersion != machineVersion) {
        if (!machineSnapshotLoaded) {
          machineFamily = machineFamilies.getLong(machineIndex);
          machineAvailableMinute =
              machineAvailable.getLong(machineIndex);
          machineSnapshotLoaded = true;
        }
        refreshCandidate(
            candidateScratch, machineIndex, resourceIndex,
            machineVersion, resourceVersion,
            machineFamily, machineAvailableMinute);
        refreshed = true;
      } else if (candidateScratch.resourceVersion
          != resourceVersion) {
        if (!advanceResourceVersionWithoutScoreChange(
            candidateScratch, resourceIndex, resourceVersion)) {
          if (!machineSnapshotLoaded) {
            machineFamily = machineFamilies.getLong(machineIndex);
            machineAvailableMinute =
                machineAvailable.getLong(machineIndex);
            machineSnapshotLoaded = true;
          }
          refreshCandidate(
              candidateScratch, machineIndex, resourceIndex,
              machineVersion, resourceVersion,
              machineFamily, machineAvailableMinute);
        }
        refreshed = true;
      }
      if (refreshed) {
        candidates.updateFrom(slot, candidateScratch);
      }
      if (betterThanMachineBest(candidateScratch)) {
        machineBest.copyFrom(candidateScratch);
      }
    }
    if (machineBest.present) {
      heap.publish(machineIndex, machineBest);
    } else {
      heap.remove(machineIndex);
    }
  }

  private void refreshCandidate(
      SelectedCandidate candidate,
      int machineIndex,
      int resourceIndex) {
    long machineVersion = machineVersions.getLong(machineIndex);
    long resourceVersion = resourceVersions.getLong(resourceIndex);
    refreshCandidate(
        candidate, machineIndex, resourceIndex,
        machineVersion, resourceVersion,
        machineFamilies.getLong(machineIndex),
        machineAvailable.getLong(machineIndex));
  }

  private void refreshCandidate(
      SelectedCandidate candidate,
      int machineIndex,
      int resourceIndex,
      long machineVersion,
      long resourceVersion,
      long machineFamily,
      long machineAvailableMinute) {
    long setup = setupValues.getLong(
        runtime.setupTimes().requireIndex(
            candidate.machineId,
            machineFamily,
            candidate.targetSetupFamily));
    long resourceReady = resourceReadyMinute(
        resourceIndex, candidate.resourceUnits);
    long earliestSetup = Math.max(
        candidate.baseReadyMinute,
        machineAvailableMinute);
    earliestSetup = Math.max(earliestSetup,
        Math.max(0L, Math.subtractExact(resourceReady, setup)));
    long setupStart = runtime.fitMachineInterval(
        machineIndex, earliestSetup,
        Math.addExact(setup, candidate.processingMinutes));
    candidate.setupMinutes = setup;
    candidate.effectiveStartMinute =
        Math.addExact(setupStart, setup);
    candidate.completionMinute = Math.addExact(
        candidate.effectiveStartMinute,
        candidate.processingMinutes);
    candidate.machineVersion = machineVersion;
    candidate.resourceVersion = resourceVersion;
  }

  private boolean advanceResourceVersionWithoutScoreChange(
      SelectedCandidate candidate,
      int resourceIndex,
      long resourceVersion) {
    long resourceReady = resourceReadyMinute(
        resourceIndex, candidate.resourceUnits);
    if (resourceReady > candidate.effectiveStartMinute) {
      return false;
    }
    candidate.resourceVersion = resourceVersion;
    return true;
  }

  private void addReleasedCandidate(
      OperationKey operation,
      long machineId,
      long targetSetupFamily,
      long resourceId,
      int resourceUnits,
      long baseReady,
      long processing,
      long transport,
      long due,
      int priority,
      long operationVersion) {
    int machineIndex =
        runtime.machineStates().requireIndex(machineId);
    int resourceIndex =
        runtime.resourceStates().requireIndex(resourceId);
    releasedCandidate.present = true;
    releasedCandidate.jobId = operation.jobId.value;
    releasedCandidate.operationId = operation.operationId.value;
    releasedCandidate.machineId = machineId;
    releasedCandidate.targetSetupFamily = targetSetupFamily;
    releasedCandidate.resourceId = resourceId;
    releasedCandidate.resourceUnits = resourceUnits;
    releasedCandidate.baseReadyMinute = baseReady;
    releasedCandidate.processingMinutes = processing;
    releasedCandidate.transportMinutes = transport;
    releasedCandidate.dueMinute = due;
    releasedCandidate.priority = priority;
    releasedCandidate.operationVersion = operationVersion;
    refreshCandidate(
        releasedCandidate, machineIndex, resourceIndex);
    candidates.add(
        releasedCandidate, machineIndex, resourceIndex);
    heap.consider(machineIndex, releasedCandidate);
  }

  private boolean betterThanMachineBest(
      SelectedCandidate candidate) {
    if (!machineBest.present) return true;
    long setupStart = Math.subtractExact(
        candidate.effectiveStartMinute,
        candidate.setupMinutes);
    long bestSetupStart = Math.subtractExact(
        machineBest.effectiveStartMinute,
        machineBest.setupMinutes);
    int result = Long.compare(setupStart, bestSetupStart);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.completionMinute,
        machineBest.completionMinute);
    if (result != 0) return result < 0;
    result = Integer.compare(
        machineBest.priority, candidate.priority);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.dueMinute, machineBest.dueMinute);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.jobId, machineBest.jobId);
    if (result != 0) return result < 0;
    result = Long.compare(
        candidate.operationId, machineBest.operationId);
    if (result != 0) return result < 0;
    return Long.compare(
        candidate.machineId, machineBest.machineId) < 0;
  }

  private long resourceReadyMinute(
      int resourceIndex, int units) {
    return resourceCalendars[resourceIndex].earliestStart(units);
  }

  private final class EligibleMachineAccess
      implements EligibleMachineScan.Consumer {
    private OperationKey operation;
    private MachineId predecessorMachine;
    private long predecessorEnd;
    private long family;
    private long resource;
    private int units;
    private long jobReady;
    private long due;
    private int priority;
    private long operationVersion;
    private boolean retiring;
    private int removed;

    void publish(
        OperationKey currentOperation,
        long currentPredecessorEnd,
        MachineId currentPredecessorMachine,
        long currentFamily,
        long currentResource,
        int currentUnits,
        long currentJobReady,
        long currentDue,
        int currentPriority,
        long currentOperationVersion) {
      open(currentOperation, false);
      predecessorEnd = currentPredecessorEnd;
      predecessorMachine = currentPredecessorMachine;
      family = currentFamily;
      resource = currentResource;
      units = currentUnits;
      jobReady = currentJobReady;
      due = currentDue;
      priority = currentPriority;
      operationVersion = currentOperationVersion;
      consume();
    }

    int retire(OperationKey currentOperation) {
      open(currentOperation, true);
      consume();
      return removed;
    }

    @Override
    public void accept(EligibleMachineCursor option) {
      long machineId = option.machineIdValue();
      if (retiring) {
        int machineIndex =
            runtime.machineStates().requireIndex(machineId);
        if (heap.represents(
            machineIndex,
            operation.jobId.value,
            operation.operationId.value,
            machineId)) {
          heap.markDirty(machineIndex);
        }
        candidates.remove(
            operation.jobId.value,
            operation.operationId.value,
            machineId);
        removed = Math.addExact(removed, 1);
        return;
      }
      long transport = predecessorMachine == null ? 0L
          : transportMinutes(predecessorMachine.value, machineId);
      long predecessorReady =
          Math.addExact(predecessorEnd, transport);
      long baseReady = Math.max(jobReady, predecessorReady);
      addReleasedCandidate(
          operation, machineId, family, resource, units,
          baseReady, option.processingMinutes(),
          transport, due, priority, operationVersion);
    }

    private void open(
        OperationKey currentOperation, boolean currentRetiring) {
      require(operation == null,
          "eligible-machine access is already active");
      operation = currentOperation;
      retiring = currentRetiring;
      removed = 0;
    }

    private void consume() {
      try {
        runtime.eligibleMachines()
            .scanByOperation(operation)
            .forEach(this);
      } finally {
        operation = null;
        predecessorMachine = null;
      }
    }
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    setupValues.close();
    resourceVersions.close();
    machineVersions.close();
    machineFamilies.close();
    machineAvailable.close();
    transportValues.close();
    jobPriorities.close();
    jobDueMinutes.close();
    jobMaterialMinutes.close();
    jobReleaseMinutes.close();
    definitionSequences.close();
    definitionOperationIds.close();
    definitionResourceUnits.close();
    definitionResourceIds.close();
    definitionFamilies.close();
    operationVersions.close();
    operationStatuses.close();
  }

  private static void require(
      boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
