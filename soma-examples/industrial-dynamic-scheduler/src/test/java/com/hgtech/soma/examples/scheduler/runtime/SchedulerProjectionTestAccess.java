package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.JobSpec;
import com.hgtech.soma.examples.scheduler.problem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.MachineSpec;
import com.hgtech.soma.examples.scheduler.problem.OperationSpec;
import com.hgtech.soma.examples.scheduler.problem.ResourceSpec;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SetupTimeSpec;
import com.hgtech.soma.examples.scheduler.problem.TransportTimeSpec;
import com.hgtech.soma.examples.scheduler.schema.JobId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.examples.scheduler.schema.OperationStatus;
import com.hgtech.soma.examples.scheduler.schema.generated.EligibleMachineCursor;
import com.hgtech.soma.examples.scheduler.schema.generated.EligibleMachineScan;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Problem 到 Runtime 投影完整性和 key/unique/child 语义的 test-only 验真。 */
public final class SchedulerProjectionTestAccess {
  private SchedulerProjectionTestAccess() {
  }

  public static void verify(SchedulingProblem problem) {
    SchedulerRuntime runtime =
        new SchedulerRuntimeFactory().create(problem);
    try {
      verifyProjection(problem, runtime);
    } finally {
      runtime.close();
    }
  }

  private static void verifyProjection(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    verifyCardinalities(problem, runtime);
    verifyJobs(problem, runtime);
    verifyOperations(problem, runtime);
    verifyMachines(problem, runtime);
    verifyResources(problem, runtime);
    verifyLookupTables(problem, runtime);
    verifyWorkflow(problem, runtime);
  }

  private static void verifyCardinalities(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    require(runtime.jobs().size() == problem.jobs().size(),
        "job projection");
    require(runtime.operationDefinitions().size()
            == problem.operationCount(),
        "operation projection");
    require(runtime.eligibleMachines().size()
            == RuntimeProjector.eligibleMachineCount(problem),
        "eligible-machine projection");
    require(runtime.operationStates().size()
            == problem.operationCount(),
        "operation state projection");
    require(runtime.machineStates().size()
            == problem.machines().size(),
        "machine state projection");
    require(runtime.resourceStates().size()
            == problem.resources().size(),
        "resource projection");
    require(runtime.setupTimes().size()
            == problem.setupTimes().size(),
        "setup projection");
    require(runtime.transportTimes().size()
            == problem.transportTimes().size(),
        "transport projection");
    require(runtime.assignmentSize() == 0,
        "result table must start empty");
  }

  private static void verifyJobs(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    LongColumnView release =
        runtime.jobs().releaseMinuteColumn();
    LongColumnView material =
        runtime.jobs().materialReadyMinuteColumn();
    LongColumnView due = runtime.jobs().dueMinuteColumn();
    IntColumnView priority = runtime.jobs().priorityColumn();
    try {
      for (JobSpec job : problem.jobs()) {
        int index = runtime.jobs().requireIndex(job.id);
        require(release.getLong(index) == job.releaseMinute
                && material.getLong(index)
                    == job.materialReadyMinute
                && due.getLong(index) == job.dueMinute
                && priority.getInt(index) == job.priority,
            "job value projection");
      }
    } finally {
      priority.close();
      due.close();
      material.close();
      release.close();
    }
  }

  private static void verifyOperations(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    IntColumnView sequences =
        runtime.operationDefinitions().sequenceNoColumn();
    LongColumnView families =
        runtime.operationDefinitions().setupFamilyValueColumn();
    LongColumnView resources =
        runtime.operationDefinitions().requiredResourceValueColumn();
    IntColumnView units =
        runtime.operationDefinitions().requiredResourceUnitsColumn();
    EnumColumnView<OperationStatus> statuses =
        runtime.operationStates().statusColumn();
    LongColumnView predecessorEnds =
        runtime.operationStates().predecessorEndMinuteColumn();
    LongColumnView predecessorMachines =
        runtime.operationStates().predecessorMachineValueColumn();
    LongColumnView versions =
        runtime.operationStates().versionColumn();
    try {
      for (OperationSpec operation : problem.operations()) {
        OperationKey key = RuntimeProjector.operationKey(operation);
        int definitionIndex =
            runtime.operationDefinitions().requireIndex(key);
        require(runtime.operationDefinitions()
                .requireIndexByJobSequence(
                    new JobId(operation.jobId), operation.sequence)
                == definitionIndex,
            "operation unique/key projection");
        require(sequences.getInt(definitionIndex)
                    == operation.sequence
                && families.getLong(definitionIndex)
                    == operation.setupFamily
                && resources.getLong(definitionIndex)
                    == operation.resourceId
                && units.getInt(definitionIndex)
                    == operation.resourceUnits,
            "operation value projection");
        verifyEligibleMachines(operation, runtime);

        int stateIndex =
            runtime.operationStates().requireIndex(key);
        require(statuses.get(stateIndex)
                    == OperationStatus.WAITING
                && predecessorEnds.getLong(stateIndex) == 0L
                && !predecessorMachines.isPresent(stateIndex)
                && versions.getLong(stateIndex) == 0L,
            "initial operation state projection");
      }
    } finally {
      versions.close();
      predecessorMachines.close();
      predecessorEnds.close();
      statuses.close();
      units.close();
      resources.close();
      families.close();
      sequences.close();
    }
  }

  private static void verifyEligibleMachines(
      OperationSpec operation, SchedulerRuntime runtime) {
    final Map<Long, Long> remaining =
        new HashMap<Long, Long>();
    for (MachineOption option : operation.options) {
      remaining.put(
          Long.valueOf(option.machineId),
          Long.valueOf(option.processingMinutes));
    }
    final int[] matched = new int[1];
    runtime.eligibleMachines()
        .scanByOperation(RuntimeProjector.operationKey(operation))
        .forEach(new EligibleMachineScan.Consumer() {
          @Override
          public void accept(EligibleMachineCursor option) {
            Long processing = remaining.remove(
                Long.valueOf(option.machineIdValue()));
            require(processing != null
                    && processing.longValue()
                        == option.processingMinutes(),
                "eligible-machine value projection");
            matched[0]++;
          }
        });
    require(matched[0] == operation.options.size()
            && remaining.isEmpty(),
        "eligible-machine group projection");
  }

  private static void verifyMachines(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    LongColumnView availability =
        runtime.machineStates().nextAvailableMinuteColumn();
    LongColumnView families =
        runtime.machineStates().lastSetupFamilyValueColumn();
    LongColumnView versions =
        runtime.machineStates().versionColumn();
    try {
      for (MachineSpec machine : problem.machines()) {
        int index =
            runtime.machineStates().requireIndex(machine.id);
        require(availability.getLong(index)
                    == machine.initialAvailableMinute
                && families.getLong(index)
                    == machine.initialSetupFamily
                && versions.getLong(index) == 0L,
            "machine state projection");
        require(runtime.machineCalendars[index]
                .matches(machine.maintenance),
            "machine calendar projection");
      }
    } finally {
      versions.close();
      families.close();
      availability.close();
    }
  }

  private static void verifyResources(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    IntColumnView capacities =
        runtime.resourceStates().capacityColumn();
    LongColumnView versions =
        runtime.resourceStates().versionColumn();
    try {
      for (ResourceSpec resource : problem.resources()) {
        int index =
            runtime.resourceStates().requireIndex(resource.id);
        require(capacities.getInt(index) == resource.capacity
                && versions.getLong(index) == 0L,
            "resource state projection");
      }
    } finally {
      versions.close();
      capacities.close();
    }
  }

  private static void verifyLookupTables(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    LongColumnView setups =
        runtime.setupTimes().setupMinutesColumn();
    LongColumnView transports =
        runtime.transportTimes().transportMinutesColumn();
    try {
      for (SetupTimeSpec setup : problem.setupTimes()) {
        int index = runtime.setupTimes().requireIndex(
            setup.machineId, setup.fromFamily, setup.toFamily);
        require(setups.getLong(index) == setup.minutes,
            "setup lookup projection");
      }
      for (TransportTimeSpec transport
          : problem.transportTimes()) {
        int index = runtime.transportTimes().requireIndex(
            transport.fromMachine, transport.toMachine);
        require(transports.getLong(index) == transport.minutes,
            "transport lookup projection");
      }
    } finally {
      transports.close();
      setups.close();
    }
  }

  private static void verifyWorkflow(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    require(runtime.jobGates.size() == problem.jobs().size(),
        "job gate cardinality");
    for (JobSpec job : problem.jobs()) {
      JobGate gate =
          runtime.jobGates.get(Long.valueOf(job.id));
      require(gate != null
              && !gate.released
              && !gate.materialReady
              && !gate.initialOperationPublished,
          "initial job gate projection");
    }

    List<ExternalEvent> expected =
        new ArrayList<ExternalEvent>(problem.events());
    Collections.sort(expected, ExternalEvent.ORDER);
    PriorityQueue<ExternalEvent> actual =
        new PriorityQueue<ExternalEvent>(
            Math.max(1, runtime.events.size()),
            ExternalEvent.ORDER);
    actual.addAll(runtime.events);
    require(actual.size() == expected.size(),
        "event cardinality projection");
    for (ExternalEvent event : expected) {
      ExternalEvent projected = actual.poll();
      require(projected != null
              && ExternalEvent.ORDER.compare(event, projected) == 0,
          "event ordering/value projection");
    }
  }

  private static void require(
      boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException(
          "runtime projection mismatch: " + label);
    }
  }
}
