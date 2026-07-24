package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.JobSpec;
import com.hgtech.soma.examples.scheduler.schema.OperationAssignment;
import com.hgtech.soma.examples.scheduler.schema.generated.DispatchCandidateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.JobDefinitionTable;
import com.hgtech.soma.examples.scheduler.schema.generated.MachineDefinitionTable;
import com.hgtech.soma.examples.scheduler.schema.generated.MachineRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.SecondaryResourceStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.SetupTimeTable;
import com.hgtech.soma.examples.scheduler.schema.generated.TransportTimeTable;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.TableStats;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** 单次 solve 的全部 SOMA tables、application structures 与 lifecycle owner。 */
public final class SchedulerRuntime implements AutoCloseable {
  final int jobCount;
  final int operationCount;
  final int maximumCandidatesPerOperation;
  final JobDefinitionTable jobs;
  final OperationDefinitionTable operationDefinitions;
  final MachineDefinitionTable machineDefinitions;
  final MachineRuntimeStateTable machineStates;
  final OperationRuntimeStateTable operationStates;
  final SecondaryResourceStateTable resourceStates;
  final SetupTimeTable setupTimes;
  final TransportTimeTable transportTimes;
  final DispatchCandidateTable frontier;
  final OperationAssignmentTable assignments;
  final PriorityQueue<ExternalEvent> events;
  final Map<Long, JobGate> jobGates;
  final MachineCalendar[] machineCalendars;
  final ResourceCalendar[] resourceCalendars;
  private boolean closed;

  SchedulerRuntime(
      SchedulingProblem problem,
      JobDefinitionTable jobs,
      OperationDefinitionTable operationDefinitions,
      MachineDefinitionTable machineDefinitions,
      MachineRuntimeStateTable machineStates,
      OperationRuntimeStateTable operationStates,
      SecondaryResourceStateTable resourceStates,
      SetupTimeTable setupTimes,
      TransportTimeTable transportTimes,
      DispatchCandidateTable frontier,
      OperationAssignmentTable assignments,
      MachineCalendar[] machineCalendars,
      ResourceCalendar[] resourceCalendars) {
    this.jobCount = problem.jobs().size();
    this.operationCount = problem.operationCount();
    this.maximumCandidatesPerOperation =
        problem.maximumCandidatesPerOperation();
    this.jobs = jobs;
    this.operationDefinitions = operationDefinitions;
    this.machineDefinitions = machineDefinitions;
    this.machineStates = machineStates;
    this.operationStates = operationStates;
    this.resourceStates = resourceStates;
    this.setupTimes = setupTimes;
    this.transportTimes = transportTimes;
    this.frontier = frontier;
    this.assignments = assignments;
    this.events = new PriorityQueue<ExternalEvent>(
        Math.max(1, problem.events().size()), ExternalEvent.ORDER);
    this.events.addAll(problem.events());
    this.jobGates = new HashMap<Long, JobGate>();
    for (JobSpec job : problem.jobs()) {
      jobGates.put(Long.valueOf(job.id), new JobGate());
    }
    this.machineCalendars = machineCalendars;
    this.resourceCalendars = resourceCalendars;
  }

  public List<OperationAssignment> exportAssignments() {
    ensureOpen();
    long rows = Math.max(1L, assignments.size());
    MaterializationBudget budget = MaterializationBudget.builder()
        .maximumRows(rows)
        .maximumLeafValues(Math.multiplyExact(rows, 32L))
        .maximumTableInstances(1L)
        .maximumOwnershipDepth(1)
        .maximumEstimatedAllocationBytes(Math.max(
            1024L * 1024L, Math.multiplyExact(rows, 512L)))
        .build();
    return assignments.fetchAll(budget);
  }

  public String schemaHash() {
    ensureOpen();
    return frontier.runtimePlan().schemaHash();
  }

  public String runtimePlanHash() {
    ensureOpen();
    return frontier.runtimePlan().runtimePlanHash();
  }

  public int assignmentKeyCount() {
    ensureOpen();
    final int[] count = new int[1];
    assignments.keys().forEach(key -> count[0] =
        Math.addExact(count[0], 1));
    return count[0];
  }

  public RuntimeEvidence runtimeEvidence() {
    ensureOpen();
    TableStats frontierStats = frontier.statsSnapshot();
    TableStats assignmentStats = assignments.statsSnapshot();
    return new RuntimeEvidence(frontierStats.exactIndexProbeCount(),
        frontierStats.exactIndexStorageHighWaterBytes(),
        frontierStats.updateScratchHighWaterBytes(),
        frontierStats.operationScratchHighWaterBytes(),
        assignmentStats.capacity(), frontierStats.capacity(),
        assignmentKeyCount());
  }

  public int jobCount() { return jobCount; }
  public int operationCount() { return operationCount; }
  public int maximumCandidatesPerOperation() {
    return maximumCandidatesPerOperation;
  }
  public JobDefinitionTable jobs() { ensureOpen(); return jobs; }
  public OperationDefinitionTable operationDefinitions() {
    ensureOpen();
    return operationDefinitions;
  }
  public MachineDefinitionTable machineDefinitions() {
    ensureOpen();
    return machineDefinitions;
  }
  public MachineRuntimeStateTable machineStates() {
    ensureOpen();
    return machineStates;
  }
  public OperationRuntimeStateTable operationStates() {
    ensureOpen();
    return operationStates;
  }
  public SecondaryResourceStateTable resourceStates() {
    ensureOpen();
    return resourceStates;
  }
  public SetupTimeTable setupTimes() { ensureOpen(); return setupTimes; }
  public TransportTimeTable transportTimes() {
    ensureOpen();
    return transportTimes;
  }
  public DispatchCandidateTable frontier() { ensureOpen(); return frontier; }
  public OperationAssignmentTable assignments() {
    ensureOpen();
    return assignments;
  }
  public int frontierSize() { ensureOpen(); return frontier.size(); }
  public int assignmentSize() { ensureOpen(); return assignments.size(); }

  public boolean hasEvents() {
    ensureOpen();
    return !events.isEmpty();
  }

  public long nextEventMinute() {
    ensureOpen();
    if (events.isEmpty()) {
      throw new IllegalStateException("no pending event");
    }
    return events.peek().minute;
  }

  public ExternalEvent pollEvent() {
    ensureOpen();
    ExternalEvent event = events.poll();
    if (event == null) throw new IllegalStateException("no pending event");
    return event;
  }

  public void markJobReleased(long jobId) {
    ensureOpen();
    requireJobGate(jobId).released = true;
  }

  public void markMaterialReady(long jobId) {
    ensureOpen();
    requireJobGate(jobId).materialReady = true;
  }

  public boolean readyToPublish(long jobId) {
    ensureOpen();
    return requireJobGate(jobId).readyToPublish();
  }

  public void markInitialOperationPublished(long jobId) {
    ensureOpen();
    requireJobGate(jobId).initialOperationPublished = true;
  }

  public long fitMachineInterval(
      int machineIndex,
      long earliestStart,
      long occupiedMinutes) {
    ensureOpen();
    return requireMachineCalendar(machineIndex).fit(
        earliestStart, occupiedMinutes);
  }

  public long earliestResourceStart(int resourceIndex, int units) {
    return requireResourceCalendar(resourceIndex).earliestStart(units);
  }

  public void commitResource(
      long resourceId, long startMinute, long endMinute, int units) {
    requireResourceCalendar(resourceStates.requireIndex(resourceId)).commit(
        startMinute, endMinute, units);
  }

  public long nextResourceAvailableMinute(long resourceId) {
    return requireResourceCalendar(
        resourceStates.requireIndex(resourceId)).nextAvailableMinute();
  }

  public int resourceCapacity(long resourceId) {
    return requireResourceCalendar(
        resourceStates.requireIndex(resourceId)).capacity();
  }

  private MachineCalendar requireMachineCalendar(int machineIndex) {
    ensureOpen();
    if (machineIndex < 0 || machineIndex >= machineCalendars.length) {
      throw new IllegalArgumentException("unknown machine Index");
    }
    MachineCalendar calendar = machineCalendars[machineIndex];
    if (calendar == null) {
      throw new IllegalStateException("missing machine calendar");
    }
    return calendar;
  }

  private ResourceCalendar requireResourceCalendar(int resourceIndex) {
    ensureOpen();
    if (resourceIndex < 0 || resourceIndex >= resourceCalendars.length) {
      throw new IllegalArgumentException("unknown resource Index");
    }
    ResourceCalendar calendar = resourceCalendars[resourceIndex];
    if (calendar == null) {
      throw new IllegalStateException("missing resource calendar");
    }
    return calendar;
  }

  private JobGate requireJobGate(long jobId) {
    JobGate gate = jobGates.get(Long.valueOf(jobId));
    if (gate == null) {
      throw new IllegalArgumentException("unknown job " + jobId);
    }
    return gate;
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("scheduler runtime is closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    assignments.release();
    frontier.release();
    transportTimes.release();
    setupTimes.release();
    resourceStates.release();
    operationStates.release();
    machineStates.release();
    machineDefinitions.release();
    operationDefinitions.release();
    jobs.release();
    events.clear();
    Arrays.fill(resourceCalendars, null);
    Arrays.fill(machineCalendars, null);
    jobGates.clear();
  }

  public static final class RuntimeEvidence {
    public final long exactIndexProbes;
    public final long exactIndexHighWaterBytes;
    public final long updateScratchHighWaterBytes;
    public final long operationScratchHighWaterBytes;
    public final int assignmentCapacity;
    public final int frontierCapacity;
    public final int assignmentKeyCount;

    RuntimeEvidence(long exactIndexProbes, long exactIndexHighWaterBytes,
                    long updateScratchHighWaterBytes,
                    long operationScratchHighWaterBytes,
                    int assignmentCapacity, int frontierCapacity,
                    int assignmentKeyCount) {
      this.exactIndexProbes = exactIndexProbes;
      this.exactIndexHighWaterBytes = exactIndexHighWaterBytes;
      this.updateScratchHighWaterBytes = updateScratchHighWaterBytes;
      this.operationScratchHighWaterBytes = operationScratchHighWaterBytes;
      this.assignmentCapacity = assignmentCapacity;
      this.frontierCapacity = frontierCapacity;
      this.assignmentKeyCount = assignmentKeyCount;
    }
  }
}
