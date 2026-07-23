package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
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
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

import java.util.HashMap;
import java.util.Map;

/** 单次 solve 的 RuntimePlan、table graph 与 lifecycle 工厂。 */
public final class SchedulerRuntimeFactory {
  public SchedulerRuntime create(SchedulingProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    RuntimePlan plan = plan(problem);
    JobDefinitionTable jobs = JobDefinitionTable.create(plan);
    OperationDefinitionTable operations =
        OperationDefinitionTable.create(plan);
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
    DispatchCandidateTable frontier =
        DispatchCandidateTable.create(plan);
    OperationAssignmentTable assignments =
        OperationAssignmentTable.create(plan);
    Map<Long, ResourceCalendar> calendars =
        new HashMap<Long, ResourceCalendar>();
    SchedulerRuntime runtime = new SchedulerRuntime(
        problem, jobs, operations, machineDefinitions, machineStates,
        operationStates, resources, setups, transports, frontier,
        assignments, calendars);
    boolean complete = false;
    try {
      new RuntimeProjector().project(problem, runtime, calendars);
      RuntimeProjectionVerifier.verify(problem, runtime);
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
            base.maximumAggregateStorageBytes(),
            2L * 1024L * 1024L * 1024L))
        .maximumOwnershipTableInstances(Math.max(
            base.maximumOwnershipTableInstances(),
            problem.operationCount()
                + problem.machines().size() + 1024L));
    replaceCapacity(
        builder, base, "job_definitions", problem.jobs().size());
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
    replaceCapacity(builder, base, "setup_times",
        problem.setupTimes().size());
    replaceCapacity(builder, base, "transport_times",
        problem.transportTimes().size());
    replaceCapacity(builder, base, "dispatch_candidates",
        problem.frontierCapacity());
    replaceCapacity(builder, base, "operation_assignments",
        problem.operationCount());
    return builder.build();
  }

  private static void replaceCapacity(
      RuntimePlan.Builder builder,
      RuntimePlan base,
      String table,
      int capacity) {
    TablePlan replacement = base.requireTable(table).toBuilder()
        .initialCapacity(Math.max(1, capacity))
        .build();
    builder.replaceTable(replacement);
  }
}
