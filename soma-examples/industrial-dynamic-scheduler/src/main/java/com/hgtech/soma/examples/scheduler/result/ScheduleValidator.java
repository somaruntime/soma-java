package com.hgtech.soma.examples.scheduler.result;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.JobInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MaintenanceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.OperationInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 不读取 SOMA runtime 内部状态的完整领域结果校验器。 */
public final class ScheduleValidator {
  private ScheduleValidator() {
  }

  public static ValidationSummary validate(
      SchedulingProblem problem, ScheduleResult claimed) {
    if (problem == null || claimed == null) {
      throw new NullPointerException("problem and claimed");
    }
    List<ScheduledOperation> assignments = claimed.assignments();
    require(assignments.size() == problem.operationCount(),
        "assignment cardinality");
    Map<String, ScheduledOperation> byOperation =
        indexAssignments(assignments);
    require(byOperation.size() == problem.operationCount(),
        "operation assigned more than once");

    long makespan = 0L;
    for (OperationInput operation : problem.operations()) {
      ScheduledOperation assignment =
          byOperation.get(identity(operation.jobId, operation.operationId));
      require(assignment != null, "missing operation assignment");
      validateAssignment(problem, operation, assignment);
      makespan = Math.max(makespan, assignment.endMinute);
    }
    validatePrecedence(problem, byOperation);
    validateMachines(problem, assignments);
    validateResources(problem, assignments);

    long tardiness = 0L;
    long weightedTardiness = 0L;
    for (JobInput job : problem.jobs()) {
      OperationInput last = problem.operation(
          job.id, job.operationCount - 1);
      long completion = byOperation.get(identity(
          last.jobId, last.operationId)).endMinute;
      long late = Math.max(0L, Math.subtractExact(completion, job.dueMinute));
      tardiness = Math.addExact(tardiness, late);
      weightedTardiness = Math.addExact(weightedTardiness,
          Math.multiplyExact(late, (long) job.priority));
    }
    String checksum = ScheduleChecksum.compute(assignments);
    require(claimed.assignmentCount() == assignments.size(),
        "claimed assignments");
    require(claimed.completedJobs == problem.jobs().size(),
        "claimed completed jobs");
    require(claimed.makespanMinute == makespan, "claimed makespan");
    require(claimed.totalTardinessMinutes == tardiness,
        "claimed tardiness");
    require(claimed.weightedTardiness == weightedTardiness,
        "claimed weighted tardiness");
    require(checksum.equals(claimed.resultChecksum),
        "claimed result checksum");
    return new ValidationSummary(assignments.size(), makespan, tardiness,
        weightedTardiness, checksum);
  }

  private static Map<String, ScheduledOperation> indexAssignments(
      List<ScheduledOperation> assignments) {
    Map<String, ScheduledOperation> result =
        new HashMap<String, ScheduledOperation>();
    for (ScheduledOperation assignment : assignments) {
      require(assignment != null, "assignment identity");
      String identity = identity(assignment.jobId,
          assignment.operationId);
      require(result.put(identity, assignment) == null,
          "duplicate assignment " + identity);
    }
    return result;
  }

  private static void validateAssignment(
      SchedulingProblem problem, OperationInput operation,
      ScheduledOperation assignment) {
    MachineOption selected = null;
    for (MachineOption option : operation.options) {
      if (option.machineId == assignment.machineId) {
        selected = option;
        break;
      }
    }
    require(selected != null, "machine is not eligible");
    require(selected.processingMinutes == assignment.processingMinutes,
        "machine-specific processing duration");
    require(operation.setupFamily == assignment.setupFamilyId,
        "setup family");
    require(operation.resourceId == assignment.resourceId,
        "secondary resource");
    JobInput job = problem.job(operation.jobId);
    require(assignment.dueMinute == job.dueMinute
            && assignment.priority == job.priority,
        "due/priority projection");
    require(assignment.setupStartMinute >= 0L
            && assignment.setupMinutes >= 0L
            && assignment.transportMinutes >= 0L,
        "non-negative timing");
    require(assignment.startMinute == Math.addExact(
        assignment.setupStartMinute, assignment.setupMinutes),
        "setup/start arithmetic");
    require(assignment.endMinute == Math.addExact(
        assignment.startMinute, assignment.processingMinutes),
        "processing/end arithmetic");
    require(assignment.setupStartMinute >= job.releaseMinute
            && assignment.setupStartMinute >= job.materialReadyMinute,
        "release/material readiness");
  }

  private static void validatePrecedence(
      SchedulingProblem problem,
      Map<String, ScheduledOperation> assignments) {
    for (JobInput job : problem.jobs()) {
      ScheduledOperation predecessor = null;
      for (int sequence = 0; sequence < job.operationCount; sequence++) {
        OperationInput operation = problem.operation(job.id, sequence);
        ScheduledOperation current = assignments.get(identity(
            operation.jobId, operation.operationId));
        if (predecessor != null) {
          long transport = problem.transportMinutes(
              predecessor.machineId, current.machineId);
          require(current.transportMinutes == transport,
              "transport duration");
          require(current.setupStartMinute >= Math.addExact(
              predecessor.endMinute, transport),
              "precedence/transport readiness");
        } else {
          require(current.transportMinutes == 0L,
              "first operation transport");
        }
        predecessor = current;
      }
    }
  }

  private static void validateMachines(
      SchedulingProblem problem, List<ScheduledOperation> assignments) {
    Map<Long, List<ScheduledOperation>> byMachine =
        new HashMap<Long, List<ScheduledOperation>>();
    for (ScheduledOperation assignment : assignments) {
      Long key = Long.valueOf(assignment.machineId);
      List<ScheduledOperation> values = byMachine.get(key);
      if (values == null) {
        values = new ArrayList<ScheduledOperation>();
        byMachine.put(key, values);
      }
      values.add(assignment);
    }
    for (MachineInput machine : problem.machines()) {
      List<ScheduledOperation> values =
          byMachine.get(Long.valueOf(machine.id));
      if (values == null) continue;
      Collections.sort(values, ASSIGNMENT_TIME_ORDER);
      long available = machine.initialAvailableMinute;
      long family = machine.initialSetupFamily;
      for (ScheduledOperation assignment : values) {
        require(assignment.setupStartMinute >= available,
            "machine overlap");
        long expectedSetup = problem.setupMinutes(
            machine.id, family, assignment.setupFamilyId);
        require(assignment.setupMinutes == expectedSetup,
            "sequence-dependent setup");
        for (MaintenanceInput maintenance : machine.maintenance) {
          require(!overlap(assignment.setupStartMinute,
              assignment.endMinute, maintenance.startMinute,
              maintenance.endMinute), "maintenance overlap");
        }
        for (ExternalEvent event : problem.events()) {
          if (event.type == SchedulingProblem.MACHINE_DELAY
              && event.subjectId == machine.id
              && event.minute <= assignment.setupStartMinute) {
            require(assignment.setupStartMinute >= event.value,
                "dynamic machine delay");
          }
        }
        available = assignment.endMinute;
        family = assignment.setupFamilyId;
      }
    }
  }

  private static void validateResources(
      SchedulingProblem problem, List<ScheduledOperation> assignments) {
    Map<Long, List<ResourceEvent>> events =
        new HashMap<Long, List<ResourceEvent>>();
    Map<String, OperationInput> inputs =
        new HashMap<String, OperationInput>();
    for (OperationInput operation : problem.operations()) {
      inputs.put(identity(operation.jobId, operation.operationId), operation);
    }
    for (ScheduledOperation assignment : assignments) {
      OperationInput input = inputs.get(identity(
          assignment.jobId, assignment.operationId));
      Long resource = Long.valueOf(input.resourceId);
      List<ResourceEvent> values = events.get(resource);
      if (values == null) {
        values = new ArrayList<ResourceEvent>();
        events.put(resource, values);
      }
      values.add(new ResourceEvent(assignment.startMinute,
          input.resourceUnits));
      values.add(new ResourceEvent(assignment.endMinute,
          -input.resourceUnits));
    }
    for (SchedulingProblem.ResourceInput resource : problem.resources()) {
      List<ResourceEvent> values = events.get(Long.valueOf(resource.id));
      if (values == null) continue;
      Collections.sort(values, ResourceEvent.ORDER);
      int used = 0;
      for (ResourceEvent event : values) {
        used = Math.addExact(used, event.delta);
        require(used >= 0 && used <= resource.capacity,
            "secondary resource capacity");
      }
      require(used == 0, "secondary resource accounting");
    }
  }

  private static boolean overlap(long leftStart, long leftEnd,
                                 long rightStart, long rightEnd) {
    return leftStart < rightEnd && leftEnd > rightStart;
  }

  private static String identity(long job, long operation) {
    return job + ":" + operation;
  }

  private static void require(boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException(
          "schedule validation failed: " + label);
    }
  }

  private static final Comparator<ScheduledOperation>
      ASSIGNMENT_TIME_ORDER = new Comparator<ScheduledOperation>() {
        @Override
        public int compare(ScheduledOperation left,
                           ScheduledOperation right) {
          int result = Long.compare(
              left.setupStartMinute, right.setupStartMinute);
          if (result != 0) return result;
          return ASSIGNMENT_IDENTITY_ORDER.compare(left, right);
        }
      };

  private static final Comparator<ScheduledOperation>
      ASSIGNMENT_IDENTITY_ORDER = new Comparator<ScheduledOperation>() {
        @Override
        public int compare(ScheduledOperation left,
                           ScheduledOperation right) {
          int result = Long.compare(left.jobId, right.jobId);
          if (result != 0) return result;
          return Long.compare(left.operationId, right.operationId);
        }
      };

  private static final class ResourceEvent {
    static final Comparator<ResourceEvent> ORDER =
        new Comparator<ResourceEvent>() {
          @Override
          public int compare(ResourceEvent left, ResourceEvent right) {
            int time = Long.compare(left.minute, right.minute);
            if (time != 0) return time;
            return Integer.compare(left.delta, right.delta);
          }
        };
    final long minute;
    final int delta;

    ResourceEvent(long minute, int delta) {
      this.minute = minute;
      this.delta = delta;
    }
  }

  public static final class ValidationSummary {
    public final int assignments;
    public final long makespanMinute;
    public final long totalTardinessMinutes;
    public final long weightedTardiness;
    public final String checksum;

    ValidationSummary(int assignments, long makespanMinute,
                      long totalTardinessMinutes, long weightedTardiness,
                      String checksum) {
      this.assignments = assignments;
      this.makespanMinute = makespanMinute;
      this.totalTardinessMinutes = totalTardinessMinutes;
      this.weightedTardiness = weightedTardiness;
      this.checksum = checksum;
    }
  }
}
