package io.github.somaruntime.soma.examples.scheduler.problem;

import io.github.somaruntime.soma.examples.scheduler.config.ProblemGenerationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 只根据 config/seed 生成 detached input，不引用 SOMA runtime。 */
public final class SyntheticSchedulingProblemFactory
    implements SchedulingProblemFactory {
  private static final long JOB_BASE = 10_000L;
  private static final long MACHINE_BASE = 20_000L;
  private static final long RESOURCE_BASE = 30_000L;
  private static final long FAMILY_BASE = 40_000L;

  @Override
  public SchedulingProblem create(ProblemGenerationConfig config) {
    if (config == null) throw new NullPointerException("config");
    Random random = new Random(config.seed());
    List<ResourceSpec> resources = resources(config, random);
    List<JobSpec> jobs = jobs(config, random);
    List<MachineSpec> machines = machines(config, random);
    List<OperationSpec> operations =
        operations(config, resources, random);
    List<SetupTimeSpec> setups = setups(config, random);
    List<TransportTimeSpec> transports = transports(config, random);
    List<ExternalEvent> events = events(config, jobs, random);
    return new SchedulingProblem(
        jobs, machines, resources, operations, setups, transports, events);
  }

  private static List<ResourceSpec> resources(
      ProblemGenerationConfig config, Random random) {
    List<ResourceSpec> result =
        new ArrayList<ResourceSpec>(config.secondaryResources());
    for (int index = 0; index < config.secondaryResources(); index++) {
      int capacity = 1 + random.nextInt(config.maxResourceCapacity());
      result.add(new ResourceSpec(RESOURCE_BASE + index, capacity));
    }
    return result;
  }

  private static List<JobSpec> jobs(
      ProblemGenerationConfig config, Random random) {
    List<JobSpec> result = new ArrayList<JobSpec>(config.jobs());
    for (int index = 0; index < config.jobs(); index++) {
      long release = bounded(random, config.releaseSpreadMinutes());
      long material = Math.addExact(release,
          bounded(random, config.materialDelayMaxMinutes()));
      long due = Math.addExact(Math.max(release, material),
          config.dueSlackMinutes());
      int priority = 1 + random.nextInt(config.priorityLevels());
      result.add(new JobSpec(JOB_BASE + index, release, material, due,
          priority, config.operationsPerJob()));
    }
    return result;
  }

  private static List<MachineSpec> machines(
      ProblemGenerationConfig config, Random random) {
    List<MachineSpec> result =
        new ArrayList<MachineSpec>(config.machines());
    long approximateLoad = divideRoundUp(
        Math.multiplyExact((long) config.operationCount(),
            (long) config.processingMaxMinutes()),
        config.machines());
    long horizon = Math.addExact(config.releaseSpreadMinutes(),
        Math.addExact(config.dueSlackMinutes(), 2L * approximateLoad));
    for (int index = 0; index < config.machines(); index++) {
      long initialFamily = FAMILY_BASE + random.nextInt(config.setupFamilies());
      long initialAvailability = bounded(random,
          Math.max(1, config.releaseSpreadMinutes() / 10));
      List<MaintenanceInterval> maintenance =
          new ArrayList<MaintenanceInterval>();
      if (config.maintenanceDurationMinutes() > 0) {
        long offset = (long) (index + 1)
            * config.maintenancePeriodMinutes() / (config.machines() + 1L);
        long start = Math.addExact(offset,
            config.maintenancePeriodMinutes());
        while (start < horizon) {
          maintenance.add(new MaintenanceInterval(start,
              Math.addExact(start, config.maintenanceDurationMinutes())));
          start = Math.addExact(start, config.maintenancePeriodMinutes());
        }
      }
      result.add(new MachineSpec(MACHINE_BASE + index, initialFamily,
          initialAvailability, maintenance));
    }
    return result;
  }

  private static List<OperationSpec> operations(
      ProblemGenerationConfig config, List<ResourceSpec> resources,
      Random random) {
    List<OperationSpec> result =
        new ArrayList<OperationSpec>(config.operationCount());
    boolean[] selected = new boolean[config.machines()];
    for (int job = 0; job < config.jobs(); job++) {
      long jobId = JOB_BASE + job;
      for (int sequence = 0; sequence < config.operationsPerJob(); sequence++) {
        for (int index = 0; index < selected.length; index++) {
          selected[index] = false;
        }
        List<MachineOption> options = new ArrayList<MachineOption>(
            config.candidateMachinesPerOperation());
        while (options.size() < config.candidateMachinesPerOperation()) {
          int machine = random.nextInt(config.machines());
          if (selected[machine]) continue;
          selected[machine] = true;
          long processing = between(random, config.processingMinMinutes(),
              config.processingMaxMinutes());
          options.add(new MachineOption(MACHINE_BASE + machine, processing));
        }
        ResourceSpec resource =
            resources.get(random.nextInt(resources.size()));
        int maximumUnits = Math.min(resource.capacity,
            config.maxResourceUnits());
        int units = 1 + random.nextInt(maximumUnits);
        long operationId = Math.addExact(
            Math.multiplyExact(jobId, 10_000L), sequence + 1L);
        result.add(new OperationSpec(jobId, operationId, sequence,
            FAMILY_BASE + random.nextInt(config.setupFamilies()),
            resource.id, units, options));
      }
    }
    return result;
  }

  private static List<SetupTimeSpec> setups(
      ProblemGenerationConfig config, Random random) {
    int count = Math.multiplyExact(config.machines(),
        Math.multiplyExact(config.setupFamilies(), config.setupFamilies()));
    List<SetupTimeSpec> result = new ArrayList<SetupTimeSpec>(count);
    for (int machine = 0; machine < config.machines(); machine++) {
      for (int from = 0; from < config.setupFamilies(); from++) {
        for (int to = 0; to < config.setupFamilies(); to++) {
          long minutes = from == to ? 0L
              : bounded(random, config.setupMaxMinutes());
          result.add(new SetupTimeSpec(MACHINE_BASE + machine,
              FAMILY_BASE + from, FAMILY_BASE + to, minutes));
        }
      }
    }
    return result;
  }

  private static List<TransportTimeSpec> transports(
      ProblemGenerationConfig config, Random random) {
    int count = Math.multiplyExact(config.machines(), config.machines());
    List<TransportTimeSpec> result =
        new ArrayList<TransportTimeSpec>(count);
    for (int from = 0; from < config.machines(); from++) {
      for (int to = 0; to < config.machines(); to++) {
        long minutes = from == to ? 0L
            : bounded(random, config.transportMaxMinutes());
        result.add(new TransportTimeSpec(MACHINE_BASE + from,
            MACHINE_BASE + to, minutes));
      }
    }
    return result;
  }

  private static List<ExternalEvent> events(
      ProblemGenerationConfig config, List<JobSpec> jobs, Random random) {
    List<ExternalEvent> result = new ArrayList<ExternalEvent>(
        Math.addExact(Math.multiplyExact(jobs.size(), 2),
            config.machineDelayEvents()));
    for (JobSpec job : jobs) {
      result.add(new ExternalEvent(job.releaseMinute,
          ExternalEventType.JOB_RELEASE, job.id, 0L));
      result.add(new ExternalEvent(job.materialReadyMinute,
          ExternalEventType.MATERIAL_READY, job.id, 0L));
    }
    for (int index = 0; index < config.machineDelayEvents(); index++) {
      long minute = bounded(random, config.releaseSpreadMinutes());
      long delayedUntil = Math.addExact(minute,
          bounded(random, config.machineDelayMaxMinutes()));
      result.add(new ExternalEvent(minute, ExternalEventType.MACHINE_DELAY,
          MACHINE_BASE + random.nextInt(config.machines()), delayedUntil));
    }
    return result;
  }

  private static long bounded(Random random, int inclusiveMaximum) {
    return inclusiveMaximum == 0
        ? 0L : random.nextInt(inclusiveMaximum + 1);
  }

  private static long between(Random random, int minimum,
                              int inclusiveMaximum) {
    return minimum + bounded(random, inclusiveMaximum - minimum);
  }

  private static long divideRoundUp(long value, int divisor) {
    return (value + divisor - 1L) / divisor;
  }
}
