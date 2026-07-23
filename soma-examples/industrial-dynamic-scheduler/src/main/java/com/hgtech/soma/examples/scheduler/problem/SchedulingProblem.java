package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.support.StableHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与 SOMA runtime 完全解耦、完成引用与范围预检的工业调度输入。
 *
 * <p>该对象只拥有不可变 Java 8 输入数据；bootstrap 之后不参与调度。</p>
 */
public final class SchedulingProblem {
  public static final int JOB_RELEASE = 1;
  public static final int MATERIAL_READY = 2;
  public static final int MACHINE_DELAY = 3;

  private final int generatorVersion;
  private final long seed;
  private final List<JobInput> jobs;
  private final List<MachineInput> machines;
  private final List<ResourceInput> resources;
  private final List<OperationInput> operations;
  private final List<SetupInput> setupTimes;
  private final List<TransportInput> transportTimes;
  private final List<ExternalEvent> events;
  private final Map<Long, JobInput> jobById;
  private final Map<Long, MachineInput> machineById;
  private final Map<Long, ResourceInput> resourceById;
  private final Map<String, OperationInput> operationBySequence;
  private final Map<String, SetupInput> setupByKey;
  private final Map<String, TransportInput> transportByKey;
  private final int maximumCandidatesPerOperation;
  private final int frontierCapacity;
  private final String checksum;

  SchedulingProblem(int generatorVersion, long seed, List<JobInput> jobs,
                    List<MachineInput> machines, List<ResourceInput> resources,
                    List<OperationInput> operations,
                    List<SetupInput> setupTimes,
                    List<TransportInput> transportTimes,
                    List<ExternalEvent> events) {
    this.generatorVersion = generatorVersion;
    this.seed = seed;
    this.jobs = immutable(jobs);
    this.machines = immutable(machines);
    this.resources = immutable(resources);
    this.operations = immutable(operations);
    this.setupTimes = immutable(setupTimes);
    this.transportTimes = immutable(transportTimes);
    ArrayList<ExternalEvent> orderedEvents =
        new ArrayList<ExternalEvent>(events);
    Collections.sort(orderedEvents, ExternalEvent.ORDER);
    this.events = Collections.unmodifiableList(orderedEvents);

    jobById = indexJobs();
    machineById = indexMachines();
    resourceById = indexResources();
    operationBySequence = indexAndValidateOperations();
    setupByKey = indexAndValidateSetups();
    transportByKey = indexAndValidateTransport();
    validateDefinitions();
    validateEvents();

    int maximumCandidates = 0;
    for (OperationInput operation : this.operations) {
      maximumCandidates = Math.max(maximumCandidates, operation.options.size());
    }
    maximumCandidatesPerOperation = maximumCandidates;
    frontierCapacity = Math.multiplyExact(this.jobs.size(), maximumCandidates);
    checksum = calculateChecksum();
  }

  private Map<Long, JobInput> indexJobs() {
    require(!jobs.isEmpty(), "at least one job is required");
    Map<Long, JobInput> result = new HashMap<Long, JobInput>();
    for (JobInput job : jobs) {
      require(job.id > 0L, "job id must be positive");
      require(job.releaseMinute >= 0L && job.materialReadyMinute >= 0L,
          "job readiness must be non-negative");
      require(job.dueMinute >= Math.max(job.releaseMinute,
          job.materialReadyMinute), "job due time precedes readiness");
      require(job.priority > 0 && job.operationCount > 0,
          "job priority and operation count must be positive");
      require(result.put(Long.valueOf(job.id), job) == null,
          "duplicate job id " + job.id);
    }
    return Collections.unmodifiableMap(result);
  }

  private Map<Long, MachineInput> indexMachines() {
    require(!machines.isEmpty(), "at least one machine is required");
    Map<Long, MachineInput> result = new HashMap<Long, MachineInput>();
    for (MachineInput machine : machines) {
      require(machine.id > 0L && machine.initialSetupFamily > 0L,
          "machine identity and family must be positive");
      require(machine.initialAvailableMinute >= 0L,
          "machine availability must be non-negative");
      long previousEnd = -1L;
      for (MaintenanceInput maintenance : machine.maintenance) {
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

  private Map<Long, ResourceInput> indexResources() {
    require(!resources.isEmpty(), "at least one secondary resource is required");
    Map<Long, ResourceInput> result = new HashMap<Long, ResourceInput>();
    for (ResourceInput resource : resources) {
      require(resource.id > 0L && resource.capacity > 0,
          "resource identity and capacity must be positive");
      require(result.put(Long.valueOf(resource.id), resource) == null,
          "duplicate resource id " + resource.id);
    }
    return Collections.unmodifiableMap(result);
  }

  private Map<String, OperationInput> indexAndValidateOperations() {
    require(!operations.isEmpty(), "at least one operation is required");
    Map<String, OperationInput> result =
        new HashMap<String, OperationInput>();
    Set<String> identities = new HashSet<String>();
    Map<Long, Integer> counts = new HashMap<Long, Integer>();
    for (OperationInput operation : operations) {
      JobInput job = jobById.get(Long.valueOf(operation.jobId));
      require(job != null, "operation references missing job");
      require(operation.operationId > 0L
              && operation.sequence >= 0
              && operation.sequence < job.operationCount,
          "invalid operation identity or sequence");
      require(operation.setupFamily > 0L,
          "operation setup family must be positive");
      ResourceInput resource =
          resourceById.get(Long.valueOf(operation.resourceId));
      require(resource != null, "operation references missing resource");
      require(operation.resourceUnits > 0
              && operation.resourceUnits <= resource.capacity,
          "operation resource demand exceeds capacity");
      require(!operation.options.isEmpty(), "operation has no eligible machine");
      Set<Long> eligible = new HashSet<Long>();
      for (MachineOption option : operation.options) {
        require(machineById.containsKey(Long.valueOf(option.machineId)),
            "operation references missing machine");
        require(option.processingMinutes > 0L,
            "processing duration must be positive");
        require(eligible.add(Long.valueOf(option.machineId)),
            "duplicate machine option for operation");
      }
      String identity = operation.jobId + ":" + operation.operationId;
      String sequence = operation.jobId + ":" + operation.sequence;
      require(identities.add(identity), "duplicate operation identity");
      require(result.put(sequence, operation) == null,
          "duplicate job sequence");
      Integer count = counts.get(Long.valueOf(operation.jobId));
      counts.put(Long.valueOf(operation.jobId),
          Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }
    for (JobInput job : jobs) {
      Integer count = counts.get(Long.valueOf(job.id));
      require(count != null && count.intValue() == job.operationCount,
          "job operation count mismatch for " + job.id);
      for (int sequence = 0; sequence < job.operationCount; sequence++) {
        require(result.containsKey(job.id + ":" + sequence),
            "job sequence is not contiguous");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private Map<String, SetupInput> indexAndValidateSetups() {
    Map<String, SetupInput> result = new HashMap<String, SetupInput>();
    Set<Long> families = new HashSet<Long>();
    for (OperationInput operation : operations) {
      families.add(Long.valueOf(operation.setupFamily));
    }
    for (MachineInput machine : machines) {
      families.add(Long.valueOf(machine.initialSetupFamily));
    }
    for (SetupInput setup : setupTimes) {
      require(machineById.containsKey(Long.valueOf(setup.machineId)),
          "setup references missing machine");
      require(setup.fromFamily > 0L && setup.toFamily > 0L
              && setup.minutes >= 0L,
          "invalid setup identity or duration");
      require(result.put(setupKey(setup.machineId, setup.fromFamily,
          setup.toFamily), setup) == null, "duplicate setup lookup");
    }
    for (MachineInput machine : machines) {
      for (Long from : families) {
        for (Long to : families) {
          require(result.containsKey(setupKey(machine.id,
              from.longValue(), to.longValue())),
              "missing setup lookup");
        }
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private Map<String, TransportInput> indexAndValidateTransport() {
    Map<String, TransportInput> result =
        new HashMap<String, TransportInput>();
    for (TransportInput transport : transportTimes) {
      require(machineById.containsKey(Long.valueOf(transport.fromMachine))
              && machineById.containsKey(Long.valueOf(transport.toMachine))
              && transport.minutes >= 0L,
          "invalid transport lookup");
      require(result.put(transportKey(transport.fromMachine,
          transport.toMachine), transport) == null,
          "duplicate transport lookup");
    }
    for (MachineInput from : machines) {
      for (MachineInput to : machines) {
        require(result.containsKey(transportKey(from.id, to.id)),
            "missing transport lookup");
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private void validateDefinitions() {
    long upperBound = 0L;
    for (JobInput job : jobs) {
      upperBound = Math.max(upperBound,
          Math.max(job.releaseMinute, job.materialReadyMinute));
    }
    for (MachineInput machine : machines) {
      upperBound = Math.max(upperBound, machine.initialAvailableMinute);
    }
    for (OperationInput operation : operations) {
      long maximum = 0L;
      for (MachineOption option : operation.options) {
        maximum = Math.max(maximum, option.processingMinutes);
      }
      upperBound = Math.addExact(upperBound, maximum);
    }
    for (SetupInput setup : setupTimes) {
      upperBound = Math.addExact(upperBound, setup.minutes);
    }
    for (TransportInput transport : transportTimes) {
      upperBound = Math.addExact(upperBound, transport.minutes);
    }
    require(upperBound >= 0L, "time upper bound overflow");
  }

  private void validateEvents() {
    Map<Long, Integer> releaseCounts = new HashMap<Long, Integer>();
    Map<Long, Integer> materialCounts = new HashMap<Long, Integer>();
    for (ExternalEvent event : events) {
      require(event.minute >= 0L, "event time must be non-negative");
      if (event.type == JOB_RELEASE || event.type == MATERIAL_READY) {
        JobInput job = jobById.get(Long.valueOf(event.subjectId));
        require(job != null, "event references missing job");
        long expected = event.type == JOB_RELEASE
            ? job.releaseMinute : job.materialReadyMinute;
        require(event.minute == expected,
            "job event does not match authoritative definition");
        Map<Long, Integer> counts = event.type == JOB_RELEASE
            ? releaseCounts : materialCounts;
        Integer count = counts.get(Long.valueOf(event.subjectId));
        counts.put(Long.valueOf(event.subjectId),
            Integer.valueOf(count == null ? 1 : count.intValue() + 1));
      } else if (event.type == MACHINE_DELAY) {
        require(machineById.containsKey(Long.valueOf(event.subjectId)),
            "delay event references missing machine");
        require(event.value >= event.minute,
            "machine delay cannot move availability backward");
      } else {
        throw new IllegalArgumentException("unknown event type " + event.type);
      }
    }
    for (JobInput job : jobs) {
      require(single(releaseCounts.get(Long.valueOf(job.id)))
              && single(materialCounts.get(Long.valueOf(job.id))),
          "each job requires one release and one material event");
    }
  }

  private static boolean single(Integer value) {
    return value != null && value.intValue() == 1;
  }

  private String calculateChecksum() {
    StableHash hash = new StableHash()
        .addString("industrial-scheduler-input-v1")
        .addInt(generatorVersion).addLong(seed);
    hash.addInt(jobs.size());
    for (JobInput job : jobs) {
      hash.addLong(job.id).addLong(job.releaseMinute)
          .addLong(job.materialReadyMinute).addLong(job.dueMinute)
          .addInt(job.priority).addInt(job.operationCount);
    }
    hash.addInt(machines.size());
    for (MachineInput machine : machines) {
      hash.addLong(machine.id).addLong(machine.initialSetupFamily)
          .addLong(machine.initialAvailableMinute)
          .addInt(machine.maintenance.size());
      for (MaintenanceInput maintenance : machine.maintenance) {
        hash.addLong(maintenance.startMinute).addLong(maintenance.endMinute);
      }
    }
    hash.addInt(resources.size());
    for (ResourceInput resource : resources) {
      hash.addLong(resource.id).addInt(resource.capacity);
    }
    hash.addInt(operations.size());
    for (OperationInput operation : operations) {
      hash.addLong(operation.jobId).addLong(operation.operationId)
          .addInt(operation.sequence).addLong(operation.setupFamily)
          .addLong(operation.resourceId).addInt(operation.resourceUnits)
          .addInt(operation.options.size());
      for (MachineOption option : operation.options) {
        hash.addLong(option.machineId).addLong(option.processingMinutes);
      }
    }
    hash.addInt(setupTimes.size());
    for (SetupInput setup : setupTimes) {
      hash.addLong(setup.machineId).addLong(setup.fromFamily)
          .addLong(setup.toFamily).addLong(setup.minutes);
    }
    hash.addInt(transportTimes.size());
    for (TransportInput transport : transportTimes) {
      hash.addLong(transport.fromMachine).addLong(transport.toMachine)
          .addLong(transport.minutes);
    }
    hash.addInt(events.size());
    for (ExternalEvent event : events) {
      hash.addLong(event.minute).addInt(event.type)
          .addLong(event.subjectId).addLong(event.value);
    }
    return hash.finishHex();
  }

  private static String setupKey(long machine, long from, long to) {
    return machine + ":" + from + ":" + to;
  }

  private static String transportKey(long from, long to) {
    return from + ":" + to;
  }

  private static <T> List<T> immutable(List<T> values) {
    if (values == null) {
      throw new NullPointerException("values");
    }
    return Collections.unmodifiableList(new ArrayList<T>(values));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  public int generatorVersion() { return generatorVersion; }
  public long seed() { return seed; }
  public List<JobInput> jobs() { return jobs; }
  public List<MachineInput> machines() { return machines; }
  public List<ResourceInput> resources() { return resources; }
  public List<OperationInput> operations() { return operations; }
  public List<SetupInput> setupTimes() { return setupTimes; }
  public List<TransportInput> transportTimes() { return transportTimes; }
  public List<ExternalEvent> events() { return events; }
  public int operationCount() { return operations.size(); }
  public int maximumCandidatesPerOperation() {
    return maximumCandidatesPerOperation;
  }
  public int frontierCapacity() { return frontierCapacity; }
  public String checksum() { return checksum; }

  public JobInput job(long id) {
    JobInput value = jobById.get(Long.valueOf(id));
    if (value == null) throw new IllegalArgumentException("unknown job " + id);
    return value;
  }

  public MachineInput machine(long id) {
    MachineInput value = machineById.get(Long.valueOf(id));
    if (value == null) {
      throw new IllegalArgumentException("unknown machine " + id);
    }
    return value;
  }

  public ResourceInput resource(long id) {
    ResourceInput value = resourceById.get(Long.valueOf(id));
    if (value == null) {
      throw new IllegalArgumentException("unknown resource " + id);
    }
    return value;
  }

  public OperationInput operation(long jobId, int sequence) {
    OperationInput value = operationBySequence.get(jobId + ":" + sequence);
    if (value == null) {
      throw new IllegalArgumentException(
          "unknown operation sequence " + jobId + ":" + sequence);
    }
    return value;
  }

  public long setupMinutes(long machine, long fromFamily, long toFamily) {
    return setupByKey.get(setupKey(machine, fromFamily, toFamily)).minutes;
  }

  public long transportMinutes(long fromMachine, long toMachine) {
    return transportByKey.get(transportKey(fromMachine, toMachine)).minutes;
  }

  public static final class JobInput {
    public final long id;
    public final long releaseMinute;
    public final long materialReadyMinute;
    public final long dueMinute;
    public final int priority;
    public final int operationCount;

    public JobInput(long id, long releaseMinute, long materialReadyMinute,
                    long dueMinute, int priority, int operationCount) {
      this.id = id;
      this.releaseMinute = releaseMinute;
      this.materialReadyMinute = materialReadyMinute;
      this.dueMinute = dueMinute;
      this.priority = priority;
      this.operationCount = operationCount;
    }
  }

  public static final class MaintenanceInput {
    public final long startMinute;
    public final long endMinute;

    public MaintenanceInput(long startMinute, long endMinute) {
      this.startMinute = startMinute;
      this.endMinute = endMinute;
    }
  }

  public static final class MachineInput {
    public final long id;
    public final long initialSetupFamily;
    public final long initialAvailableMinute;
    public final List<MaintenanceInput> maintenance;

    public MachineInput(long id, long initialSetupFamily,
                        long initialAvailableMinute,
                        List<MaintenanceInput> maintenance) {
      this.id = id;
      this.initialSetupFamily = initialSetupFamily;
      this.initialAvailableMinute = initialAvailableMinute;
      this.maintenance = immutable(maintenance);
    }
  }

  public static final class ResourceInput {
    public final long id;
    public final int capacity;

    public ResourceInput(long id, int capacity) {
      this.id = id;
      this.capacity = capacity;
    }
  }

  public static final class MachineOption {
    public final long machineId;
    public final long processingMinutes;

    public MachineOption(long machineId, long processingMinutes) {
      this.machineId = machineId;
      this.processingMinutes = processingMinutes;
    }
  }

  public static final class OperationInput {
    public final long jobId;
    public final long operationId;
    public final int sequence;
    public final long setupFamily;
    public final long resourceId;
    public final int resourceUnits;
    public final List<MachineOption> options;

    public OperationInput(long jobId, long operationId, int sequence,
                          long setupFamily, long resourceId,
                          int resourceUnits, List<MachineOption> options) {
      this.jobId = jobId;
      this.operationId = operationId;
      this.sequence = sequence;
      this.setupFamily = setupFamily;
      this.resourceId = resourceId;
      this.resourceUnits = resourceUnits;
      this.options = immutable(options);
    }
  }

  public static final class SetupInput {
    public final long machineId;
    public final long fromFamily;
    public final long toFamily;
    public final long minutes;

    public SetupInput(long machineId, long fromFamily, long toFamily,
                      long minutes) {
      this.machineId = machineId;
      this.fromFamily = fromFamily;
      this.toFamily = toFamily;
      this.minutes = minutes;
    }
  }

  public static final class TransportInput {
    public final long fromMachine;
    public final long toMachine;
    public final long minutes;

    public TransportInput(long fromMachine, long toMachine, long minutes) {
      this.fromMachine = fromMachine;
      this.toMachine = toMachine;
      this.minutes = minutes;
    }
  }

  public static final class ExternalEvent {
    public static final Comparator<ExternalEvent> ORDER =
        new Comparator<ExternalEvent>() {
          @Override
          public int compare(ExternalEvent left, ExternalEvent right) {
            int byTime = Long.compare(left.minute, right.minute);
            if (byTime != 0) return byTime;
            int byType = Integer.compare(left.type, right.type);
            if (byType != 0) return byType;
            int bySubject = Long.compare(left.subjectId, right.subjectId);
            if (bySubject != 0) return bySubject;
            return Long.compare(left.value, right.value);
          }
        };

    public final long minute;
    public final int type;
    public final long subjectId;
    public final long value;

    public ExternalEvent(long minute, int type, long subjectId, long value) {
      this.minute = minute;
      this.type = type;
      this.subjectId = subjectId;
      this.value = value;
    }
  }
}
