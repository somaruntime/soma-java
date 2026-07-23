package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.JobSpec;
import com.hgtech.soma.examples.scheduler.problem.MachineSpec;
import com.hgtech.soma.examples.scheduler.problem.OperationSpec;
import com.hgtech.soma.examples.scheduler.problem.ResourceSpec;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.schema.JobId;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

/** Problem 到 Runtime 投影完整性和 key/unique/child 语义的验真。 */
final class RuntimeProjectionVerifier {
  private RuntimeProjectionVerifier() {
  }

  static void verify(
      SchedulingProblem problem, SchedulerRuntime runtime) {
    require(runtime.jobs().size() == problem.jobs().size(),
        "job projection");
    require(runtime.operationDefinitions().size()
            == problem.operationCount(),
        "operation projection");
    require(runtime.operationStates().size()
            == problem.operationCount(),
        "operation state projection");
    require(runtime.machineDefinitions().size()
            == problem.machines().size(),
        "machine projection");
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
    require(runtime.frontierSize() == 0
            && runtime.assignmentSize() == 0,
        "derived/result tables must start empty");

    LongColumnView due = runtime.jobs().dueMinuteColumn();
    IntColumnView priority = runtime.jobs().priorityColumn();
    try {
      for (JobSpec job : problem.jobs()) {
        int index = runtime.jobs().requireIndex(job.id);
        require(due.getLong(index) == job.dueMinute
                && priority.getInt(index) == job.priority,
            "job value projection");
      }
    } finally {
      priority.close();
      due.close();
    }
    for (OperationSpec operation : problem.operations()) {
      OperationKey key = RuntimeProjector.operationKey(operation);
      require(runtime.operationDefinitions()
              .requireIndexByJobSequence(
                  new JobId(operation.jobId), operation.sequence)
              == runtime.operationDefinitions().requireIndex(key),
          "operation unique/key projection");
      require(runtime.operationDefinitions()
              .eligibleMachines(key).size()
              == operation.options.size(),
          "eligible child projection");
      require(runtime.operationStates().requireIndex(key) >= 0,
          "operation state key projection");
    }
    for (MachineSpec machine : problem.machines()) {
      MachineId key = new MachineId(machine.id);
      require(runtime.machineDefinitions()
              .maintenanceWindows(key).size()
              == machine.maintenance.size(),
          "maintenance child projection");
      require(runtime.machineStates().requireIndex(key) >= 0,
          "machine state key projection");
    }
    for (ResourceSpec resource : problem.resources()) {
      require(runtime.resourceCapacity(resource.id)
              == resource.capacity,
          "resource calendar projection");
    }
  }

  private static void require(boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException(
          "runtime projection mismatch: " + label);
    }
  }
}
