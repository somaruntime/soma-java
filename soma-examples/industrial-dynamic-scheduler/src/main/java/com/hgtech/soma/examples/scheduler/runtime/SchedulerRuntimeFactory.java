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
import com.hgtech.soma.examples.scheduler.schema.generated.SchemaMetadata;
import com.hgtech.soma.examples.scheduler.schema.generated.SetupTimeTable;
import com.hgtech.soma.examples.scheduler.schema.generated.TransportTimeTable;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaGroup;
import com.hgtech.soma.runtime.SomaGroupPlan;

/** 单次 solve 的 RuntimePlan、table graph 与 lifecycle 工厂。 */
public final class SchedulerRuntimeFactory {
  public SchedulerRuntime create(SchedulingProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    RuntimePlan plan = plan(problem);
    SomaGroup group = SomaGroup.create(groupPlan(plan));
    SchedulerRuntime runtime = null;
    try {
      JobDefinitionTable jobs =
          JobDefinitionTable.attach(group, "job-definitions");
      OperationDefinitionTable operations =
          OperationDefinitionTable.attach(group, "operation-definitions");
      EligibleMachineTable eligibleMachines =
          EligibleMachineTable.attach(group, "eligible-machines");
      MachineRuntimeStateTable machineStates =
          MachineRuntimeStateTable.attach(group, "machine-states");
      OperationRuntimeStateTable operationStates =
          OperationRuntimeStateTable.attach(group, "operation-states");
      SecondaryResourceStateTable resources =
          SecondaryResourceStateTable.attach(group, "resource-states");
      SetupTimeTable setups =
          SetupTimeTable.attach(group, "setup-times");
      TransportTimeTable transports =
          TransportTimeTable.attach(group, "transport-times");
      OperationAssignmentTable assignments =
          OperationAssignmentTable.attach(group, "assignments");
      MachineCalendar[] machineCalendars =
          new MachineCalendar[problem.machines().size()];
      for (int index = 0; index < machineCalendars.length; index++) {
        MachineSpec machine = problem.machines().get(index);
        machineCalendars[index] =
            new MachineCalendar(machine.maintenance);
      }
      runtime = new SchedulerRuntime(
          problem, group, jobs, operations, eligibleMachines,
          machineStates, operationStates, resources, setups,
          transports, assignments, machineCalendars);
      new RuntimeProjector().project(problem, runtime);
      return runtime;
    } catch (RuntimeException failure) {
      cleanup(runtime, group, failure);
      throw failure;
    } catch (Error failure) {
      cleanup(runtime, group, failure);
      throw failure;
    }
  }

  private static SomaGroupPlan groupPlan(RuntimePlan plan) {
    return SomaGroupPlan.builder("industrial-scheduler-solve")
        .member("job-definitions", SchemaMetadata.metadata(),
            JobDefinitionTable.metadata(), plan)
        .member("operation-definitions", SchemaMetadata.metadata(),
            OperationDefinitionTable.metadata(), plan)
        .member("eligible-machines", SchemaMetadata.metadata(),
            EligibleMachineTable.metadata(), plan)
        .member("machine-states", SchemaMetadata.metadata(),
            MachineRuntimeStateTable.metadata(), plan)
        .member("operation-states", SchemaMetadata.metadata(),
            OperationRuntimeStateTable.metadata(), plan)
        .member("resource-states", SchemaMetadata.metadata(),
            SecondaryResourceStateTable.metadata(), plan)
        .member("setup-times", SchemaMetadata.metadata(),
            SetupTimeTable.metadata(), plan)
        .member("transport-times", SchemaMetadata.metadata(),
            TransportTimeTable.metadata(), plan)
        .member("assignments", SchemaMetadata.metadata(),
            OperationAssignmentTable.metadata(), plan)
        .build();
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

  private static void cleanup(
      SchedulerRuntime runtime,
      SomaGroup group,
      Throwable primary) {
    try {
      if (runtime != null) {
        runtime.close();
      } else {
        group.release();
      }
    } catch (Throwable cleanup) {
      if (cleanup != primary) primary.addSuppressed(cleanup);
    }
  }
}
