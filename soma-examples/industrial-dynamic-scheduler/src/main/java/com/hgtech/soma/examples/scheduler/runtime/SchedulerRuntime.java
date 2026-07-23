package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;
import com.hgtech.soma.examples.scheduler.state.generated.DispatchCandidateTable;
import com.hgtech.soma.examples.scheduler.state.generated.JobDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.MachineDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.MachineRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.SecondaryResourceStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.SetupTimeTable;
import com.hgtech.soma.examples.scheduler.state.generated.TransportTimeTable;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.TableStats;

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
  final Map<Long, ResourceCalendar> resourceCalendars;
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
      Map<Long, ResourceCalendar> resourceCalendars) {
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
    for (SchedulingProblem.JobInput job : problem.jobs()) {
      jobGates.put(Long.valueOf(job.id), new JobGate());
    }
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
    resourceCalendars.clear();
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
