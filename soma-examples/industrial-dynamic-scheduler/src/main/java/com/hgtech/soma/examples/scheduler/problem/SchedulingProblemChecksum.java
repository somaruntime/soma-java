package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.support.StableHash;

import java.util.List;

/** SchedulingProblem 稳定输入 identity 的唯一 Owner。 */
final class SchedulingProblemChecksum {
  private SchedulingProblemChecksum() {
  }

  static String compute(
      int generatorVersion,
      long seed,
      List<JobSpec> jobs,
      List<MachineSpec> machines,
      List<ResourceSpec> resources,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      List<TransportTimeSpec> transportTimes,
      List<ExternalEvent> events) {
    StableHash hash = new StableHash()
        .addString("industrial-scheduler-input-v1")
        .addInt(generatorVersion).addLong(seed);
    hash.addInt(jobs.size());
    for (JobSpec job : jobs) {
      hash.addLong(job.id).addLong(job.releaseMinute)
          .addLong(job.materialReadyMinute).addLong(job.dueMinute)
          .addInt(job.priority).addInt(job.operationCount);
    }
    hash.addInt(machines.size());
    for (MachineSpec machine : machines) {
      hash.addLong(machine.id).addLong(machine.initialSetupFamily)
          .addLong(machine.initialAvailableMinute)
          .addInt(machine.maintenance.size());
      for (MaintenanceInterval maintenance : machine.maintenance) {
        hash.addLong(maintenance.startMinute)
            .addLong(maintenance.endMinute);
      }
    }
    hash.addInt(resources.size());
    for (ResourceSpec resource : resources) {
      hash.addLong(resource.id).addInt(resource.capacity);
    }
    hash.addInt(operations.size());
    for (OperationSpec operation : operations) {
      hash.addLong(operation.jobId).addLong(operation.operationId)
          .addInt(operation.sequence).addLong(operation.setupFamily)
          .addLong(operation.resourceId).addInt(operation.resourceUnits)
          .addInt(operation.options.size());
      for (MachineOption option : operation.options) {
        hash.addLong(option.machineId)
            .addLong(option.processingMinutes);
      }
    }
    hash.addInt(setupTimes.size());
    for (SetupTimeSpec setup : setupTimes) {
      hash.addLong(setup.machineId).addLong(setup.fromFamily)
          .addLong(setup.toFamily).addLong(setup.minutes);
    }
    hash.addInt(transportTimes.size());
    for (TransportTimeSpec transport : transportTimes) {
      hash.addLong(transport.fromMachine)
          .addLong(transport.toMachine)
          .addLong(transport.minutes);
    }
    hash.addInt(events.size());
    for (ExternalEvent event : events) {
      hash.addLong(event.minute).addInt(event.type.code())
          .addLong(event.subjectId).addLong(event.value);
    }
    return hash.finishHex();
  }
}
