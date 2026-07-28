package io.github.somaruntime.soma.examples.scheduler.problem;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** SchedulingProblem 的引用闭包、范围与容量派生边界。 */
final class SchedulingProblemValidator {
  private SchedulingProblemValidator() {
  }

  static SchedulingProblemIndex validateAndIndex(
      List<JobSpec> jobs,
      List<MachineSpec> machines,
      List<ResourceSpec> resources,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      List<TransportTimeSpec> transportTimes,
      List<ExternalEvent> events) {
    Map<Long, JobSpec> jobById = indexJobs(jobs);
    Map<Long, MachineSpec> machineById = indexMachines(machines);
    Map<Long, ResourceSpec> resourceById = indexResources(resources);
    Map<String, OperationSpec> operationBySequence =
        indexOperations(jobs, operations, jobById, machineById, resourceById);
    Map<String, SetupTimeSpec> setupByKey =
        indexSetups(machines, operations, setupTimes, machineById);
    Map<String, TransportTimeSpec> transportByKey =
        indexTransports(machines, transportTimes, machineById);
    validateEvents(jobs, events, jobById, machineById);
    validateTimeArithmetic(
        jobs, machines, operations, setupTimes, transportTimes, events);

    int maximumCandidates = 0;
    for (OperationSpec operation : operations) {
      maximumCandidates = Math.max(
          maximumCandidates, operation.options.size());
    }
    return new SchedulingProblemIndex(
        jobById, machineById, resourceById, operationBySequence,
        setupByKey, transportByKey, maximumCandidates,
        Math.multiplyExact(jobs.size(), maximumCandidates));
  }

  private static Map<Long, JobSpec> indexJobs(List<JobSpec> jobs) {
    require(!jobs.isEmpty(), "at least one job is required");
    Map<Long, JobSpec> result = new HashMap<Long, JobSpec>();
    for (JobSpec job : jobs) {
      require(job != null, "job must not be null");
      require(job.id > 0L, "job id must be positive");
      require(job.releaseMinute >= 0L && job.materialReadyMinute >= 0L,
          "job readiness must be non-negative");
      require(job.dueMinute >= Math.max(
          job.releaseMinute, job.materialReadyMinute),
          "job due time precedes readiness");
      require(job.priority > 0 && job.operationCount > 0,
          "job priority and operation count must be positive");
      require(result.put(Long.valueOf(job.id), job) == null,
          "duplicate job id " + job.id);
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<Long, MachineSpec> indexMachines(
      List<MachineSpec> machines) {
    require(!machines.isEmpty(), "at least one machine is required");
    Map<Long, MachineSpec> result = new HashMap<Long, MachineSpec>();
    for (MachineSpec machine : machines) {
      require(machine != null, "machine must not be null");
      require(machine.id > 0L && machine.initialSetupFamily > 0L,
          "machine identity and family must be positive");
      require(machine.initialAvailableMinute >= 0L,
          "machine availability must be non-negative");
      long previousEnd = -1L;
      for (MaintenanceInterval maintenance : machine.maintenance) {
        require(maintenance != null, "maintenance must not be null");
        require(maintenance.startMinute >= 0L
                && maintenance.endMinute > maintenance.startMinute,
            "invalid maintenance interval");
        require(maintenance.startMinute >= previousEnd,
            "maintenance intervals overlap or are unordered");
        previousEnd = maintenance.endMinute;
      }
      require(result.put(Long.valueOf(machine.id), machine) == null,
          "duplicate machine id " + machine.id);
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<Long, ResourceSpec> indexResources(
      List<ResourceSpec> resources) {
    require(!resources.isEmpty(),
        "at least one secondary resource is required");
    Map<Long, ResourceSpec> result = new HashMap<Long, ResourceSpec>();
    for (ResourceSpec resource : resources) {
      require(resource != null, "resource must not be null");
      require(resource.id > 0L && resource.capacity > 0,
          "resource identity and capacity must be positive");
      require(result.put(Long.valueOf(resource.id), resource) == null,
          "duplicate resource id " + resource.id);
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, OperationSpec> indexOperations(
      List<JobSpec> jobs,
      List<OperationSpec> operations,
      Map<Long, JobSpec> jobById,
      Map<Long, MachineSpec> machineById,
      Map<Long, ResourceSpec> resourceById) {
    require(!operations.isEmpty(), "at least one operation is required");
    Map<String, OperationSpec> result =
        new HashMap<String, OperationSpec>();
    Set<String> identities = new HashSet<String>();
    Map<Long, Integer> counts = new HashMap<Long, Integer>();
    for (OperationSpec operation : operations) {
      require(operation != null, "operation must not be null");
      JobSpec job = jobById.get(Long.valueOf(operation.jobId));
      require(job != null, "operation references missing job");
      require(operation.operationId > 0L
              && operation.sequence >= 0
              && operation.sequence < job.operationCount,
          "invalid operation identity or sequence");
      require(operation.setupFamily > 0L,
          "operation setup family must be positive");
      ResourceSpec resource =
          resourceById.get(Long.valueOf(operation.resourceId));
      require(resource != null, "operation references missing resource");
      require(operation.resourceUnits > 0
              && operation.resourceUnits <= resource.capacity,
          "operation resource demand exceeds capacity");
      require(!operation.options.isEmpty(),
          "operation has no eligible machine");
      Set<Long> eligible = new HashSet<Long>();
      for (MachineOption option : operation.options) {
        require(option != null, "machine option must not be null");
        require(machineById.containsKey(Long.valueOf(option.machineId)),
            "operation references missing machine");
        require(option.processingMinutes > 0L,
            "processing duration must be positive");
        require(eligible.add(Long.valueOf(option.machineId)),
            "duplicate machine option for operation");
      }
      String identity = identity(operation.jobId, operation.operationId);
      String sequence = sequence(operation.jobId, operation.sequence);
      require(identities.add(identity), "duplicate operation identity");
      require(result.put(sequence, operation) == null,
          "duplicate job sequence");
      Integer count = counts.get(Long.valueOf(operation.jobId));
      counts.put(Long.valueOf(operation.jobId),
          Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }
    for (JobSpec job : jobs) {
      Integer count = counts.get(Long.valueOf(job.id));
      require(count != null && count.intValue() == job.operationCount,
          "job operation count mismatch for " + job.id);
      for (int index = 0; index < job.operationCount; index++) {
        require(result.containsKey(sequence(job.id, index)),
            "job sequence is not contiguous");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, SetupTimeSpec> indexSetups(
      List<MachineSpec> machines,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      Map<Long, MachineSpec> machineById) {
    Map<String, SetupTimeSpec> result =
        new HashMap<String, SetupTimeSpec>();
    Set<Long> families = new HashSet<Long>();
    for (OperationSpec operation : operations) {
      families.add(Long.valueOf(operation.setupFamily));
    }
    for (MachineSpec machine : machines) {
      families.add(Long.valueOf(machine.initialSetupFamily));
    }
    for (SetupTimeSpec setup : setupTimes) {
      require(setup != null, "setup must not be null");
      require(machineById.containsKey(Long.valueOf(setup.machineId)),
          "setup references missing machine");
      require(setup.fromFamily > 0L && setup.toFamily > 0L
              && setup.minutes >= 0L,
          "invalid setup identity or duration");
      require(result.put(setupKey(
          setup.machineId, setup.fromFamily, setup.toFamily), setup) == null,
          "duplicate setup lookup");
    }
    for (MachineSpec machine : machines) {
      for (Long from : families) {
        for (Long to : families) {
          require(result.containsKey(setupKey(
              machine.id, from.longValue(), to.longValue())),
              "missing setup lookup");
        }
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, TransportTimeSpec> indexTransports(
      List<MachineSpec> machines,
      List<TransportTimeSpec> transportTimes,
      Map<Long, MachineSpec> machineById) {
    Map<String, TransportTimeSpec> result =
        new HashMap<String, TransportTimeSpec>();
    for (TransportTimeSpec transport : transportTimes) {
      require(transport != null, "transport must not be null");
      require(machineById.containsKey(Long.valueOf(transport.fromMachine))
              && machineById.containsKey(Long.valueOf(transport.toMachine))
              && transport.minutes >= 0L,
          "invalid transport lookup");
      require(result.put(transportKey(
          transport.fromMachine, transport.toMachine), transport) == null,
          "duplicate transport lookup");
    }
    for (MachineSpec from : machines) {
      for (MachineSpec to : machines) {
        require(result.containsKey(transportKey(from.id, to.id)),
            "missing transport lookup");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static void validateTimeArithmetic(
      List<JobSpec> jobs,
      List<MachineSpec> machines,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      List<TransportTimeSpec> transportTimes,
      List<ExternalEvent> events) {
    try {
      long horizon = 0L;
      for (JobSpec job : jobs) {
        horizon = Math.max(horizon,
            Math.max(job.releaseMinute, job.materialReadyMinute));
      }
      for (MachineSpec machine : machines) {
        horizon = Math.max(horizon, machine.initialAvailableMinute);
        for (MaintenanceInterval maintenance : machine.maintenance) {
          horizon = Math.max(horizon, maintenance.endMinute);
        }
      }
      for (ExternalEvent event : events) {
        horizon = Math.max(horizon,
            Math.max(event.minute, event.value));
      }
      long maximumSetup = 0L;
      for (SetupTimeSpec setup : setupTimes) {
        maximumSetup = Math.max(maximumSetup, setup.minutes);
      }
      long maximumTransport = 0L;
      for (TransportTimeSpec transport : transportTimes) {
        maximumTransport = Math.max(
            maximumTransport, transport.minutes);
      }
      for (OperationSpec operation : operations) {
        long maximumProcessing = 0L;
        for (MachineOption option : operation.options) {
          maximumProcessing = Math.max(
              maximumProcessing, option.processingMinutes);
        }
        horizon = Math.addExact(horizon, maximumTransport);
        horizon = Math.addExact(horizon, maximumSetup);
        horizon = Math.addExact(horizon, maximumProcessing);
      }
      long totalTardinessBound = 0L;
      long weightedTardinessBound = 0L;
      for (JobSpec job : jobs) {
        totalTardinessBound =
            Math.addExact(totalTardinessBound, horizon);
        weightedTardinessBound = Math.addExact(
            weightedTardinessBound,
            Math.multiplyExact(horizon, (long) job.priority));
      }
      require(totalTardinessBound >= 0L
              && weightedTardinessBound >= 0L,
          "invalid aggregate time bound");
    } catch (ArithmeticException overflow) {
      throw new IllegalArgumentException(
          "time arithmetic exceeds signed long range", overflow);
    }
  }

  private static void validateEvents(
      List<JobSpec> jobs,
      List<ExternalEvent> events,
      Map<Long, JobSpec> jobById,
      Map<Long, MachineSpec> machineById) {
    Map<Long, Integer> releaseCounts = new HashMap<Long, Integer>();
    Map<Long, Integer> materialCounts = new HashMap<Long, Integer>();
    for (ExternalEvent event : events) {
      require(event != null, "event must not be null");
      require(event.minute >= 0L, "event time must be non-negative");
      if (event.type == ExternalEventType.JOB_RELEASE
          || event.type == ExternalEventType.MATERIAL_READY) {
        JobSpec job = jobById.get(Long.valueOf(event.subjectId));
        require(job != null, "event references missing job");
        long expected = event.type == ExternalEventType.JOB_RELEASE
            ? job.releaseMinute : job.materialReadyMinute;
        require(event.minute == expected,
            "job event does not match authoritative definition");
        Map<Long, Integer> counts =
            event.type == ExternalEventType.JOB_RELEASE
                ? releaseCounts : materialCounts;
        Integer count = counts.get(Long.valueOf(event.subjectId));
        counts.put(Long.valueOf(event.subjectId),
            Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      } else if (event.type == ExternalEventType.MACHINE_DELAY) {
        require(machineById.containsKey(Long.valueOf(event.subjectId)),
            "delay event references missing machine");
        require(event.value >= event.minute,
            "machine delay cannot move availability backward");
      } else {
        throw new IllegalArgumentException(
            "unknown event type " + event.type);
      }
    }
    for (JobSpec job : jobs) {
      require(single(releaseCounts.get(Long.valueOf(job.id)))
              && single(materialCounts.get(Long.valueOf(job.id))),
          "each job requires one release and one material event");
    }
  }

  private static boolean single(Integer value) {
    return value != null && value.intValue() == 1;
  }

  static String sequence(long job, int operationSequence) {
    return job + ":" + operationSequence;
  }

  static String setupKey(long machine, long from, long to) {
    return machine + ":" + from + ":" + to;
  }

  static String transportKey(long from, long to) {
    return from + ":" + to;
  }

  private static String identity(long job, long operation) {
    return job + ":" + operation;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
