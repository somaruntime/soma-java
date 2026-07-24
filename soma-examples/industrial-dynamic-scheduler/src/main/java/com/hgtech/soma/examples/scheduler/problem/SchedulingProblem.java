package com.hgtech.soma.examples.scheduler.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 与 SOMA runtime 完全解耦、完成引用与范围预检的工业调度输入。
 */
public final class SchedulingProblem {
  private final List<JobSpec> jobs;
  private final List<MachineSpec> machines;
  private final List<ResourceSpec> resources;
  private final List<OperationSpec> operations;
  private final List<SetupTimeSpec> setupTimes;
  private final List<TransportTimeSpec> transportTimes;
  private final List<ExternalEvent> events;
  private final SchedulingProblemIndex index;
  private final String checksum;

  public SchedulingProblem(
      List<JobSpec> jobs,
      List<MachineSpec> machines,
      List<ResourceSpec> resources,
      List<OperationSpec> operations,
      List<SetupTimeSpec> setupTimes,
      List<TransportTimeSpec> transportTimes,
      List<ExternalEvent> events) {
    this.jobs = immutable(jobs);
    this.machines = immutable(machines);
    this.resources = immutable(resources);
    this.operations = immutable(operations);
    this.setupTimes = immutable(setupTimes);
    this.transportTimes = immutable(transportTimes);
    ArrayList<ExternalEvent> orderedEvents =
        new ArrayList<ExternalEvent>(requireList(events));
    Collections.sort(orderedEvents, ExternalEvent.ORDER);
    this.events = Collections.unmodifiableList(orderedEvents);
    index = SchedulingProblemValidator.validateAndIndex(
        this.jobs, this.machines, this.resources, this.operations,
        this.setupTimes, this.transportTimes, this.events);
    checksum = SchedulingProblemChecksum.compute(
        this.jobs, this.machines, this.resources, this.operations,
        this.setupTimes, this.transportTimes, this.events);
  }

  public List<JobSpec> jobs() { return jobs; }
  public List<MachineSpec> machines() { return machines; }
  public List<ResourceSpec> resources() { return resources; }
  public List<OperationSpec> operations() { return operations; }
  public List<SetupTimeSpec> setupTimes() { return setupTimes; }
  public List<TransportTimeSpec> transportTimes() { return transportTimes; }
  public List<ExternalEvent> events() { return events; }
  public int operationCount() { return operations.size(); }
  public int maximumCandidatesPerOperation() {
    return index.maximumCandidatesPerOperation;
  }
  public int frontierCapacity() { return index.frontierCapacity; }
  public String checksum() { return checksum; }

  public JobSpec job(long id) {
    JobSpec value = index.jobs.get(Long.valueOf(id));
    if (value == null) throw new IllegalArgumentException("unknown job " + id);
    return value;
  }

  public MachineSpec machine(long id) {
    MachineSpec value = index.machines.get(Long.valueOf(id));
    if (value == null) {
      throw new IllegalArgumentException("unknown machine " + id);
    }
    return value;
  }

  public ResourceSpec resource(long id) {
    ResourceSpec value = index.resources.get(Long.valueOf(id));
    if (value == null) {
      throw new IllegalArgumentException("unknown resource " + id);
    }
    return value;
  }

  public OperationSpec operation(long jobId, int sequence) {
    OperationSpec value = index.operationsBySequence.get(
        SchedulingProblemValidator.sequence(jobId, sequence));
    if (value == null) {
      throw new IllegalArgumentException(
          "unknown operation sequence " + jobId + ":" + sequence);
    }
    return value;
  }

  public long setupMinutes(
      long machine, long fromFamily, long toFamily) {
    SetupTimeSpec value = index.setups.get(
        SchedulingProblemValidator.setupKey(
            machine, fromFamily, toFamily));
    if (value == null) throw new IllegalArgumentException("unknown setup");
    return value.minutes;
  }

  public long transportMinutes(long fromMachine, long toMachine) {
    TransportTimeSpec value = index.transports.get(
        SchedulingProblemValidator.transportKey(
            fromMachine, toMachine));
    if (value == null) {
      throw new IllegalArgumentException("unknown transport");
    }
    return value.minutes;
  }

  private static <T> List<T> immutable(List<T> values) {
    return Collections.unmodifiableList(
        new ArrayList<T>(requireList(values)));
  }

  private static <T> List<T> requireList(List<T> values) {
    if (values == null) throw new NullPointerException("values");
    return values;
  }
}
