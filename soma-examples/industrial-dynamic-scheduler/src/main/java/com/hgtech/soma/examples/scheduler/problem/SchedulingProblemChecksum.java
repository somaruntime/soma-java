package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.support.StableHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** SchedulingProblem 稳定输入 identity 的唯一 Owner。 */
final class SchedulingProblemChecksum {
  private SchedulingProblemChecksum() {
  }

  static String compute(
      List<JobSpec> jobs,
      List<MachineSpec> machines,
      List<ResourceSpec> resources,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      List<TransportTimeSpec> transportTimes,
      List<ExternalEvent> events) {
    StableHash hash = new StableHash()
        .addString("industrial-scheduler-input-v2");
    List<JobSpec> orderedJobs = ordered(jobs, JOB_ORDER);
    List<MachineSpec> orderedMachines = ordered(machines, MACHINE_ORDER);
    List<ResourceSpec> orderedResources = ordered(resources, RESOURCE_ORDER);
    List<OperationSpec> orderedOperations =
        ordered(operations, OPERATION_ORDER);
    List<SetupTimeSpec> orderedSetups = ordered(setupTimes, SETUP_ORDER);
    List<TransportTimeSpec> orderedTransports =
        ordered(transportTimes, TRANSPORT_ORDER);
    List<ExternalEvent> orderedEvents = ordered(events, ExternalEvent.ORDER);

    hash.addInt(orderedJobs.size());
    for (JobSpec job : orderedJobs) {
      hash.addLong(job.id).addLong(job.releaseMinute)
          .addLong(job.materialReadyMinute).addLong(job.dueMinute)
          .addInt(job.priority).addInt(job.operationCount);
    }
    hash.addInt(orderedMachines.size());
    for (MachineSpec machine : orderedMachines) {
      hash.addLong(machine.id).addLong(machine.initialSetupFamily)
          .addLong(machine.initialAvailableMinute)
          .addInt(machine.maintenance.size());
      for (MaintenanceInterval maintenance : machine.maintenance) {
        hash.addLong(maintenance.startMinute)
            .addLong(maintenance.endMinute);
      }
    }
    hash.addInt(orderedResources.size());
    for (ResourceSpec resource : orderedResources) {
      hash.addLong(resource.id).addInt(resource.capacity);
    }
    hash.addInt(orderedOperations.size());
    for (OperationSpec operation : orderedOperations) {
      hash.addLong(operation.jobId).addLong(operation.operationId)
          .addInt(operation.sequence).addLong(operation.setupFamily)
          .addLong(operation.resourceId).addInt(operation.resourceUnits);
      List<MachineOption> options =
          ordered(operation.options, MACHINE_OPTION_ORDER);
      hash.addInt(options.size());
      for (MachineOption option : options) {
        hash.addLong(option.machineId)
            .addLong(option.processingMinutes);
      }
    }
    hash.addInt(orderedSetups.size());
    for (SetupTimeSpec setup : orderedSetups) {
      hash.addLong(setup.machineId).addLong(setup.fromFamily)
          .addLong(setup.toFamily).addLong(setup.minutes);
    }
    hash.addInt(orderedTransports.size());
    for (TransportTimeSpec transport : orderedTransports) {
      hash.addLong(transport.fromMachine)
          .addLong(transport.toMachine)
          .addLong(transport.minutes);
    }
    hash.addInt(orderedEvents.size());
    for (ExternalEvent event : orderedEvents) {
      hash.addLong(event.minute).addInt(event.type.code())
          .addLong(event.subjectId).addLong(event.value);
    }
    return hash.finishHex();
  }

  private static <T> List<T> ordered(
      List<T> values, Comparator<? super T> comparator) {
    ArrayList<T> result = new ArrayList<T>(values);
    Collections.sort(result, comparator);
    return result;
  }

  private static final Comparator<JobSpec> JOB_ORDER =
      new Comparator<JobSpec>() {
        @Override
        public int compare(JobSpec left, JobSpec right) {
          return Long.compare(left.id, right.id);
        }
      };

  private static final Comparator<MachineSpec> MACHINE_ORDER =
      new Comparator<MachineSpec>() {
        @Override
        public int compare(MachineSpec left, MachineSpec right) {
          return Long.compare(left.id, right.id);
        }
      };

  private static final Comparator<ResourceSpec> RESOURCE_ORDER =
      new Comparator<ResourceSpec>() {
        @Override
        public int compare(ResourceSpec left, ResourceSpec right) {
          return Long.compare(left.id, right.id);
        }
      };

  private static final Comparator<OperationSpec> OPERATION_ORDER =
      new Comparator<OperationSpec>() {
        @Override
        public int compare(OperationSpec left, OperationSpec right) {
          int job = Long.compare(left.jobId, right.jobId);
          if (job != 0) return job;
          return Long.compare(left.operationId, right.operationId);
        }
      };

  private static final Comparator<MachineOption> MACHINE_OPTION_ORDER =
      new Comparator<MachineOption>() {
        @Override
        public int compare(MachineOption left, MachineOption right) {
          return Long.compare(left.machineId, right.machineId);
        }
      };

  private static final Comparator<SetupTimeSpec> SETUP_ORDER =
      new Comparator<SetupTimeSpec>() {
        @Override
        public int compare(SetupTimeSpec left, SetupTimeSpec right) {
          int machine = Long.compare(left.machineId, right.machineId);
          if (machine != 0) return machine;
          int from = Long.compare(left.fromFamily, right.fromFamily);
          if (from != 0) return from;
          return Long.compare(left.toFamily, right.toFamily);
        }
      };

  private static final Comparator<TransportTimeSpec> TRANSPORT_ORDER =
      new Comparator<TransportTimeSpec>() {
        @Override
        public int compare(
            TransportTimeSpec left, TransportTimeSpec right) {
          int from = Long.compare(left.fromMachine, right.fromMachine);
          if (from != 0) return from;
          return Long.compare(left.toMachine, right.toMachine);
        }
      };
}
