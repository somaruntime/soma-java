package io.github.somaruntime.soma.examples.scheduler.problem;

import java.util.Map;

/** SchedulingProblem 内部的只读 lookup 与容量派生事实。 */
final class SchedulingProblemIndex {
  final Map<Long, JobSpec> jobs;
  final Map<Long, MachineSpec> machines;
  final Map<Long, ResourceSpec> resources;
  final Map<String, OperationSpec> operationsBySequence;
  final Map<String, SetupTimeSpec> setups;
  final Map<String, TransportTimeSpec> transports;
  final int maximumCandidatesPerOperation;
  final int frontierCapacity;

  SchedulingProblemIndex(
      Map<Long, JobSpec> jobs,
      Map<Long, MachineSpec> machines,
      Map<Long, ResourceSpec> resources,
      Map<String, OperationSpec> operationsBySequence,
      Map<String, SetupTimeSpec> setups,
      Map<String, TransportTimeSpec> transports,
      int maximumCandidatesPerOperation,
      int frontierCapacity) {
    this.jobs = jobs;
    this.machines = machines;
    this.resources = resources;
    this.operationsBySequence = operationsBySequence;
    this.setups = setups;
    this.transports = transports;
    this.maximumCandidatesPerOperation = maximumCandidatesPerOperation;
    this.frontierCapacity = frontierCapacity;
  }
}
