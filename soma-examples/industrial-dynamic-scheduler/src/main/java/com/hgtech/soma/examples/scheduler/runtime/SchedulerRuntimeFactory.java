package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.MachineSpec;
import com.hgtech.soma.examples.scheduler.schema.generated.JobDefinitionTable;
import com.hgtech.soma.examples.scheduler.schema.generated.EligibleMachineTable;
import com.hgtech.soma.examples.scheduler.schema.generated.MachineRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationDefinitionTable;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationRuntimeStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.SecondaryResourceStateTable;
import com.hgtech.soma.examples.scheduler.schema.generated.SetupTimeTable;
import com.hgtech.soma.examples.scheduler.schema.generated.TransportTimeTable;
import com.hgtech.soma.runtime.RuntimePlan;

/** 单次 solve 的 RuntimePlan、table graph 与 lifecycle 工厂。 */
public final class SchedulerRuntimeFactory {
  public SchedulerRuntime create(SchedulingProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    RuntimePlan plan = plan(problem);
    JobDefinitionTable jobs = JobDefinitionTable.create(plan);
    OperationDefinitionTable operations =
        OperationDefinitionTable.create(plan);
    EligibleMachineTable eligibleMachines =
        EligibleMachineTable.create(plan);
    MachineRuntimeStateTable machineStates =
        MachineRuntimeStateTable.create(plan);
    OperationRuntimeStateTable operationStates =
        OperationRuntimeStateTable.create(plan);
    SecondaryResourceStateTable resources =
        SecondaryResourceStateTable.create(plan);
    SetupTimeTable setups = SetupTimeTable.create(plan);
    TransportTimeTable transports = TransportTimeTable.create(plan);
    OperationAssignmentTable assignments =
        OperationAssignmentTable.create(plan);
    MachineCalendar[] machineCalendars =
        new MachineCalendar[problem.machines().size()];
    for (int index = 0; index < machineCalendars.length; index++) {
      MachineSpec machine = problem.machines().get(index);
      machineCalendars[index] = new MachineCalendar(machine.maintenance);
    }
    SchedulerRuntime runtime = new SchedulerRuntime(
        problem, jobs, operations, eligibleMachines, machineStates,
        operationStates, resources, setups, transports,
        assignments, machineCalendars);
    boolean complete = false;
    try {
      new RuntimeProjector().project(problem, runtime);
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
            2L * 1024L * 1024L * 1024L));
    setCapacity(
        builder, "job_definitions", problem.jobs().size());
    setCapacity(builder, "operation_definitions",
        problem.operationCount());
    setCapacity(builder, "eligible_machines",
        RuntimeProjector.eligibleMachineCount(problem));
    setCapacity(builder, "machine_runtime_states",
        problem.machines().size());
    setCapacity(builder, "operation_runtime_states",
        problem.operationCount());
    setCapacity(builder, "secondary_resource_states",
        problem.resources().size());
    setCapacity(builder, "setup_times",
        problem.setupTimes().size());
    setCapacity(builder, "transport_times",
        problem.transportTimes().size());
    setCapacity(builder, "operation_assignments",
        problem.operationCount());
    return builder.build();
  }

  private static void setCapacity(
      RuntimePlan.Builder builder,
      String table,
      int capacity) {
    builder.table(table).initialCapacity(Math.max(1, capacity));
  }
}
