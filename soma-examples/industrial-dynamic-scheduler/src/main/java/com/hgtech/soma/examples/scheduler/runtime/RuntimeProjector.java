package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.JobSpec;
import com.hgtech.soma.examples.scheduler.problem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.MachineSpec;
import com.hgtech.soma.examples.scheduler.problem.MaintenanceInterval;
import com.hgtech.soma.examples.scheduler.problem.OperationSpec;
import com.hgtech.soma.examples.scheduler.problem.ResourceSpec;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SetupTimeSpec;
import com.hgtech.soma.examples.scheduler.problem.TransportTimeSpec;
import com.hgtech.soma.examples.scheduler.schema.JobId;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.MachinePairKey;
import com.hgtech.soma.examples.scheduler.schema.OperationId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.examples.scheduler.schema.OperationStatus;
import com.hgtech.soma.examples.scheduler.schema.ResourceId;
import com.hgtech.soma.examples.scheduler.schema.SetupFamilyId;
import com.hgtech.soma.examples.scheduler.schema.SetupTimeKey;
import com.hgtech.soma.examples.scheduler.schema.generated.EligibleMachineBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.JobDefinitionBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.MachineDefinitionBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.MachineRuntimeStateBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.MaintenanceWindowBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationDefinitionBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.OperationRuntimeStateBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.SecondaryResourceStateBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.SetupTimeBatch;
import com.hgtech.soma.examples.scheduler.schema.generated.TransportTimeBatch;

import java.util.Map;

/** detached Problem 到 authoritative SOMA tables 的唯一投影边界。 */
final class RuntimeProjector {
  private static final int BATCH_SIZE = 512;

  void project(
      SchedulingProblem problem,
      SchedulerRuntime runtime,
      Map<Long, ResourceCalendar> calendars) {
    reserve(problem, runtime);
    importJobs(problem, runtime);
    importMachines(problem, runtime);
    importResources(problem, runtime, calendars);
    importOperations(problem, runtime);
    importSetups(problem, runtime);
    importTransport(problem, runtime);
  }

  private static void reserve(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    runtime.jobs().reserve(problem.jobs().size());
    runtime.operationDefinitions().reserve(problem.operationCount());
    runtime.machineDefinitions().reserve(problem.machines().size());
    runtime.machineStates().reserve(problem.machines().size());
    runtime.operationStates().reserve(problem.operationCount());
    runtime.resourceStates().reserve(problem.resources().size());
    runtime.setupTimes().reserve(problem.setupTimes().size());
    runtime.transportTimes().reserve(problem.transportTimes().size());
    runtime.frontier().reserve(problem.frontierCapacity());
    runtime.assignments().reserve(problem.operationCount());
  }

  private static void importJobs(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    JobDefinitionBatch batch = new JobDefinitionBatch(
        Math.min(BATCH_SIZE, problem.jobs().size()));
    for (JobSpec job : problem.jobs()) {
      batch.addValues(new JobId(job.id), job.releaseMinute,
          job.materialReadyMinute, job.dueMinute, job.priority,
          job.operationCount);
      if (batch.size() == BATCH_SIZE) {
        runtime.jobs().addBatch(batch);
        batch.clear();
      }
    }
    runtime.jobs().addBatch(batch);
  }

  private static void importMachines(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    MachineDefinitionBatch definitions = new MachineDefinitionBatch(
        Math.min(BATCH_SIZE, problem.machines().size()));
    MachineRuntimeStateBatch states = new MachineRuntimeStateBatch(
        Math.min(BATCH_SIZE, problem.machines().size()));
    for (MachineSpec machine : problem.machines()) {
      MaintenanceWindowBatch maintenance =
          new MaintenanceWindowBatch(machine.maintenance.size());
      for (MaintenanceInterval window : machine.maintenance) {
        maintenance.addValues(
            window.startMinute, window.endMinute);
      }
      MachineId machineId = new MachineId(machine.id);
      SetupFamilyId initialFamily =
          new SetupFamilyId(machine.initialSetupFamily);
      definitions.addValues(machineId, initialFamily, maintenance);
      states.addValues(machineId, machine.initialAvailableMinute,
          true, initialFamily, 0L);
      if (definitions.size() == BATCH_SIZE) {
        runtime.machineDefinitions().addBatch(definitions);
        runtime.machineStates().addBatch(states);
        definitions.clear();
        states.clear();
      }
    }
    runtime.machineDefinitions().addBatch(definitions);
    runtime.machineStates().addBatch(states);
  }

  private static void importResources(
      SchedulingProblem problem,
      SchedulerRuntime runtime,
      Map<Long, ResourceCalendar> calendars) {
    SecondaryResourceStateBatch batch =
        new SecondaryResourceStateBatch(
            Math.min(BATCH_SIZE, problem.resources().size()));
    for (ResourceSpec resource : problem.resources()) {
      batch.addValues(new ResourceId(resource.id),
          resource.capacity, 0L, 0L);
      calendars.put(Long.valueOf(resource.id),
          new ResourceCalendar(resource.capacity));
    }
    runtime.resourceStates().addBatch(batch);
  }

  private static void importOperations(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    OperationDefinitionBatch definitions =
        new OperationDefinitionBatch(
            Math.min(BATCH_SIZE, problem.operationCount()));
    OperationRuntimeStateBatch states =
        new OperationRuntimeStateBatch(
            Math.min(BATCH_SIZE, problem.operationCount()));
    for (OperationSpec operation : problem.operations()) {
      EligibleMachineBatch eligible =
          new EligibleMachineBatch(operation.options.size());
      for (MachineOption option : operation.options) {
        eligible.addValues(
            new MachineId(option.machineId),
            option.processingMinutes);
      }
      OperationKey key = operationKey(operation);
      definitions.addValues(key, operation.sequence,
          new SetupFamilyId(operation.setupFamily),
          new ResourceId(operation.resourceId),
          operation.resourceUnits, eligible);
      states.addValues(
          key, OperationStatus.WAITING, 0L, false, null, 0L);
      if (definitions.size() == BATCH_SIZE) {
        runtime.operationDefinitions().addBatch(definitions);
        runtime.operationStates().addBatch(states);
        definitions.clear();
        states.clear();
      }
    }
    runtime.operationDefinitions().addBatch(definitions);
    runtime.operationStates().addBatch(states);
  }

  private static void importSetups(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    SetupTimeBatch batch = new SetupTimeBatch(
        Math.min(BATCH_SIZE, problem.setupTimes().size()));
    for (SetupTimeSpec setup : problem.setupTimes()) {
      batch.addValues(
          new SetupTimeKey(
              new MachineId(setup.machineId),
              new SetupFamilyId(setup.fromFamily),
              new SetupFamilyId(setup.toFamily)),
          setup.minutes);
      if (batch.size() == BATCH_SIZE) {
        runtime.setupTimes().addBatch(batch);
        batch.clear();
      }
    }
    runtime.setupTimes().addBatch(batch);
  }

  private static void importTransport(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    TransportTimeBatch batch = new TransportTimeBatch(
        Math.min(BATCH_SIZE, problem.transportTimes().size()));
    for (TransportTimeSpec transport : problem.transportTimes()) {
      batch.addValues(
          new MachinePairKey(
              new MachineId(transport.fromMachine),
              new MachineId(transport.toMachine)),
          transport.minutes);
      if (batch.size() == BATCH_SIZE) {
        runtime.transportTimes().addBatch(batch);
        batch.clear();
      }
    }
    runtime.transportTimes().addBatch(batch);
  }

  static OperationKey operationKey(OperationSpec operation) {
    return new OperationKey(
        new JobId(operation.jobId),
        new OperationId(operation.operationId));
  }
}
