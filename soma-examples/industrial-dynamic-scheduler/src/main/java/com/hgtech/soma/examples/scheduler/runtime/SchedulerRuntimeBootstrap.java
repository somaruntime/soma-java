package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.JobInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.OperationInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ResourceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.SetupInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.TransportInput;
import com.hgtech.soma.examples.scheduler.state.JobId;
import com.hgtech.soma.examples.scheduler.state.MachineId;
import com.hgtech.soma.examples.scheduler.state.MachinePairKey;
import com.hgtech.soma.examples.scheduler.state.OperationId;
import com.hgtech.soma.examples.scheduler.state.OperationKey;
import com.hgtech.soma.examples.scheduler.state.OperationStatus;
import com.hgtech.soma.examples.scheduler.state.ResourceId;
import com.hgtech.soma.examples.scheduler.state.SetupFamilyId;
import com.hgtech.soma.examples.scheduler.state.SetupTimeKey;
import com.hgtech.soma.examples.scheduler.state.generated.DispatchCandidateTable;
import com.hgtech.soma.examples.scheduler.state.generated.EligibleMachineBatch;
import com.hgtech.soma.examples.scheduler.state.generated.JobDefinitionBatch;
import com.hgtech.soma.examples.scheduler.state.generated.JobDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.MachineDefinitionBatch;
import com.hgtech.soma.examples.scheduler.state.generated.MachineDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.MachineRuntimeStateBatch;
import com.hgtech.soma.examples.scheduler.state.generated.MachineRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.MaintenanceWindowBatch;
import com.hgtech.soma.examples.scheduler.state.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.scheduler.state.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.scheduler.state.generated.OperationRuntimeStateBatch;
import com.hgtech.soma.examples.scheduler.state.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.SecondaryResourceStateBatch;
import com.hgtech.soma.examples.scheduler.state.generated.SecondaryResourceStateTable;
import com.hgtech.soma.examples.scheduler.state.generated.SetupTimeBatch;
import com.hgtech.soma.examples.scheduler.state.generated.SetupTimeTable;
import com.hgtech.soma.examples.scheduler.state.generated.TransportTimeBatch;
import com.hgtech.soma.examples.scheduler.state.generated.TransportTimeTable;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

import java.util.HashMap;
import java.util.Map;

/** validated detached input 到 authoritative SOMA state 的唯一装载边界。 */
public final class SchedulerRuntimeBootstrap {
  private static final int BATCH_SIZE = 512;

  private SchedulerRuntimeBootstrap() {
  }

  public static SchedulerRuntime load(SchedulingProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    RuntimePlan plan = plan(problem);
    JobDefinitionTable jobs = JobDefinitionTable.create(plan);
    OperationDefinitionTable operations = OperationDefinitionTable.create(plan);
    MachineDefinitionTable machineDefinitions =
        MachineDefinitionTable.create(plan);
    MachineRuntimeStateTable machineStates =
        MachineRuntimeStateTable.create(plan);
    OperationRuntimeStateTable operationStates =
        OperationRuntimeStateTable.create(plan);
    SecondaryResourceStateTable resources =
        SecondaryResourceStateTable.create(plan);
    SetupTimeTable setups = SetupTimeTable.create(plan);
    TransportTimeTable transports = TransportTimeTable.create(plan);
    DispatchCandidateTable frontier = DispatchCandidateTable.create(plan);
    OperationAssignmentTable assignments =
        OperationAssignmentTable.create(plan);
    Map<Long, ResourceCalendar> calendars =
        new HashMap<Long, ResourceCalendar>();
    SchedulerRuntime runtime = new SchedulerRuntime(problem, jobs, operations,
        machineDefinitions, machineStates, operationStates, resources, setups,
        transports, frontier, assignments, calendars);
    boolean complete = false;
    try {
      reserve(problem, runtime);
      importJobs(problem, runtime);
      importMachines(problem, runtime);
      importResources(problem, runtime, calendars);
      importOperations(problem, runtime);
      importSetups(problem, runtime);
      importTransport(problem, runtime);
      verifyProjection(problem, runtime);
      complete = true;
      return runtime;
    } finally {
      if (!complete) runtime.close();
    }
  }

  private static RuntimePlan plan(SchedulingProblem problem) {
    RuntimePlan base = JobDefinitionTable.defaultRuntimePlan();
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(), 2L * 1024L * 1024L * 1024L))
        .maximumOwnershipTableInstances(Math.max(
            base.maximumOwnershipTableInstances(),
            problem.operationCount() + problem.machines().size() + 1024L));
    replaceCapacity(builder, base, "job_definitions", problem.jobs().size());
    replaceCapacity(builder, base, "operation_definitions",
        problem.operationCount());
    replaceCapacity(builder, base, "machine_definitions",
        problem.machines().size());
    replaceCapacity(builder, base, "machine_runtime_states",
        problem.machines().size());
    replaceCapacity(builder, base, "operation_runtime_states",
        problem.operationCount());
    replaceCapacity(builder, base, "secondary_resource_states",
        problem.resources().size());
    replaceCapacity(builder, base, "setup_times", problem.setupTimes().size());
    replaceCapacity(builder, base, "transport_times",
        problem.transportTimes().size());
    replaceCapacity(builder, base, "dispatch_candidates",
        problem.frontierCapacity());
    replaceCapacity(builder, base, "operation_assignments",
        problem.operationCount());
    return builder.build();
  }

  private static void replaceCapacity(RuntimePlan.Builder builder,
                                      RuntimePlan base, String table,
                                      int capacity) {
    TablePlan replacement = base.requireTable(table).toBuilder()
        .initialCapacity(Math.max(1, capacity)).build();
    builder.replaceTable(replacement);
  }

  private static void reserve(SchedulingProblem problem,
                              SchedulerRuntime runtime) {
    runtime.jobs.reserve(problem.jobs().size());
    runtime.operationDefinitions.reserve(problem.operationCount());
    runtime.machineDefinitions.reserve(problem.machines().size());
    runtime.machineStates.reserve(problem.machines().size());
    runtime.operationStates.reserve(problem.operationCount());
    runtime.resourceStates.reserve(problem.resources().size());
    runtime.setupTimes.reserve(problem.setupTimes().size());
    runtime.transportTimes.reserve(problem.transportTimes().size());
    runtime.frontier.reserve(problem.frontierCapacity());
    runtime.assignments.reserve(problem.operationCount());
  }

  private static void importJobs(SchedulingProblem problem,
                                 SchedulerRuntime runtime) {
    JobDefinitionBatch batch = new JobDefinitionBatch(
        Math.min(BATCH_SIZE, problem.jobs().size()));
    for (JobInput job : problem.jobs()) {
      batch.addValues(new JobId(job.id), job.releaseMinute,
          job.materialReadyMinute, job.dueMinute, job.priority,
          job.operationCount);
      if (batch.size() == BATCH_SIZE) {
        runtime.jobs.addBatch(batch);
        batch.clear();
      }
    }
    runtime.jobs.addBatch(batch);
  }

  private static void importMachines(SchedulingProblem problem,
                                     SchedulerRuntime runtime) {
    MachineDefinitionBatch definitions = new MachineDefinitionBatch(
        Math.min(BATCH_SIZE, problem.machines().size()));
    MachineRuntimeStateBatch states = new MachineRuntimeStateBatch(
        Math.min(BATCH_SIZE, problem.machines().size()));
    for (MachineInput machine : problem.machines()) {
      MaintenanceWindowBatch maintenance =
          new MaintenanceWindowBatch(machine.maintenance.size());
      for (SchedulingProblem.MaintenanceInput window : machine.maintenance) {
        maintenance.addValues(window.startMinute, window.endMinute);
      }
      MachineId machineId = new MachineId(machine.id);
      SetupFamilyId initialFamily =
          new SetupFamilyId(machine.initialSetupFamily);
      definitions.addValues(machineId, initialFamily, maintenance);
      states.addValues(machineId, machine.initialAvailableMinute,
          true, initialFamily, 0L);
      if (definitions.size() == BATCH_SIZE) {
        runtime.machineDefinitions.addBatch(definitions);
        runtime.machineStates.addBatch(states);
        definitions.clear();
        states.clear();
      }
    }
    runtime.machineDefinitions.addBatch(definitions);
    runtime.machineStates.addBatch(states);
  }

  private static void importResources(
      SchedulingProblem problem, SchedulerRuntime runtime,
      Map<Long, ResourceCalendar> calendars) {
    SecondaryResourceStateBatch batch = new SecondaryResourceStateBatch(
        Math.min(BATCH_SIZE, problem.resources().size()));
    for (ResourceInput resource : problem.resources()) {
      batch.addValues(new ResourceId(resource.id), resource.capacity, 0L, 0L);
      calendars.put(Long.valueOf(resource.id),
          new ResourceCalendar(resource.capacity));
    }
    runtime.resourceStates.addBatch(batch);
  }

  private static void importOperations(SchedulingProblem problem,
                                       SchedulerRuntime runtime) {
    OperationDefinitionBatch definitions = new OperationDefinitionBatch(
        Math.min(BATCH_SIZE, problem.operationCount()));
    OperationRuntimeStateBatch states = new OperationRuntimeStateBatch(
        Math.min(BATCH_SIZE, problem.operationCount()));
    for (OperationInput operation : problem.operations()) {
      EligibleMachineBatch eligible =
          new EligibleMachineBatch(operation.options.size());
      for (MachineOption option : operation.options) {
        eligible.addValues(new MachineId(option.machineId),
            option.processingMinutes);
      }
      OperationKey key = operationKey(operation);
      definitions.addValues(key, operation.sequence,
          new SetupFamilyId(operation.setupFamily),
          new ResourceId(operation.resourceId), operation.resourceUnits,
          eligible);
      states.addValues(key, OperationStatus.WAITING, 0L,
          false, null, 0L);
      if (definitions.size() == BATCH_SIZE) {
        runtime.operationDefinitions.addBatch(definitions);
        runtime.operationStates.addBatch(states);
        definitions.clear();
        states.clear();
      }
    }
    runtime.operationDefinitions.addBatch(definitions);
    runtime.operationStates.addBatch(states);
  }

  private static void importSetups(SchedulingProblem problem,
                                   SchedulerRuntime runtime) {
    SetupTimeBatch batch = new SetupTimeBatch(
        Math.min(BATCH_SIZE, problem.setupTimes().size()));
    for (SetupInput setup : problem.setupTimes()) {
      batch.addValues(new SetupTimeKey(new MachineId(setup.machineId),
          new SetupFamilyId(setup.fromFamily),
          new SetupFamilyId(setup.toFamily)), setup.minutes);
      if (batch.size() == BATCH_SIZE) {
        runtime.setupTimes.addBatch(batch);
        batch.clear();
      }
    }
    runtime.setupTimes.addBatch(batch);
  }

  private static void importTransport(SchedulingProblem problem,
                                      SchedulerRuntime runtime) {
    TransportTimeBatch batch = new TransportTimeBatch(
        Math.min(BATCH_SIZE, problem.transportTimes().size()));
    for (TransportInput transport : problem.transportTimes()) {
      batch.addValues(new MachinePairKey(
          new MachineId(transport.fromMachine),
          new MachineId(transport.toMachine)), transport.minutes);
      if (batch.size() == BATCH_SIZE) {
        runtime.transportTimes.addBatch(batch);
        batch.clear();
      }
    }
    runtime.transportTimes.addBatch(batch);
  }

  private static void verifyProjection(SchedulingProblem problem,
                                       SchedulerRuntime runtime) {
    require(runtime.jobs.size() == problem.jobs().size(), "job projection");
    require(runtime.operationDefinitions.size() == problem.operationCount(),
        "operation projection");
    require(runtime.operationStates.size() == problem.operationCount(),
        "operation state projection");
    require(runtime.machineDefinitions.size() == problem.machines().size(),
        "machine projection");
    require(runtime.machineStates.size() == problem.machines().size(),
        "machine state projection");
    require(runtime.resourceStates.size() == problem.resources().size(),
        "resource projection");
    require(runtime.setupTimes.size() == problem.setupTimes().size(),
        "setup projection");
    require(runtime.transportTimes.size() == problem.transportTimes().size(),
        "transport projection");
    require(runtime.frontier.size() == 0 && runtime.assignments.size() == 0,
        "derived/result tables must start empty");

    LongColumnView due = runtime.jobs.dueMinuteColumn();
    IntColumnView priority = runtime.jobs.priorityColumn();
    try {
      for (JobInput job : problem.jobs()) {
        int row = runtime.jobs.requireIndex(job.id);
        require(due.getLong(row) == job.dueMinute
                && priority.getInt(row) == job.priority,
            "job value projection");
      }
    } finally {
      priority.close();
      due.close();
    }
    for (OperationInput operation : problem.operations()) {
      OperationKey key = operationKey(operation);
      require(runtime.operationDefinitions.requireIndexByJobSequence(
          new JobId(operation.jobId), operation.sequence)
          == runtime.operationDefinitions.requireIndex(key),
          "operation unique/key projection");
      require(runtime.operationDefinitions.eligibleMachines(key).size()
          == operation.options.size(), "eligible child projection");
      require(runtime.operationStates.requireIndex(key) >= 0,
          "operation state key projection");
    }
    for (MachineInput machine : problem.machines()) {
      MachineId key = new MachineId(machine.id);
      require(runtime.machineDefinitions.maintenanceWindows(key).size()
          == machine.maintenance.size(), "maintenance child projection");
      require(runtime.machineStates.requireIndex(key) >= 0,
          "machine state key projection");
    }
    for (ResourceInput resource : problem.resources()) {
      ResourceCalendar calendar =
          runtime.resourceCalendars.get(Long.valueOf(resource.id));
      require(calendar != null && calendar.capacity() == resource.capacity,
          "resource calendar projection");
    }
  }

  static OperationKey operationKey(OperationInput operation) {
    return new OperationKey(new JobId(operation.jobId),
        new OperationId(operation.operationId));
  }

  private static void require(boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException(
          "bootstrap projection mismatch: " + label);
    }
  }
}
