package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.config.SchedulerConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.JobInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MaintenanceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.OperationInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ResourceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.SetupInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.TransportInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 只根据 config/seed 生成 detached input，不引用 SOMA runtime。 */
public final class SchedulingProblemGenerator {
  private static final long JOB_BASE = 10_000L;
  private static final long MACHINE_BASE = 20_000L;
  private static final long RESOURCE_BASE = 30_000L;
  private static final long FAMILY_BASE = 40_000L;

  private SchedulingProblemGenerator() {
  }

  public static SchedulingProblem generate(SchedulerConfig config) {
    if (config == null) throw new NullPointerException("config");
    Random random = new Random(config.seed());
    List<ResourceInput> resources = resources(config, random);
    List<JobInput> jobs = jobs(config, random);
    List<MachineInput> machines = machines(config, random);
    List<OperationInput> operations =
        operations(config, resources, random);
    List<SetupInput> setups = setups(config, random);
    List<TransportInput> transports = transports(config, random);
    List<ExternalEvent> events = events(config, jobs, random);
    return new SchedulingProblem(config.generatorVersion(), config.seed(),
        jobs, machines, resources, operations, setups, transports, events);
  }

  private static List<ResourceInput> resources(
      SchedulerConfig config, Random random) {
    List<ResourceInput> result =
        new ArrayList<ResourceInput>(config.secondaryResources());
    for (int index = 0; index < config.secondaryResources(); index++) {
      int capacity = 1 + random.nextInt(config.maxResourceCapacity());
      result.add(new ResourceInput(RESOURCE_BASE + index, capacity));
    }
    return result;
  }

  private static List<JobInput> jobs(
      SchedulerConfig config, Random random) {
    List<JobInput> result = new ArrayList<JobInput>(config.jobs());
    for (int index = 0; index < config.jobs(); index++) {
      long release = bounded(random, config.releaseSpreadMinutes());
      long material = Math.addExact(release,
          bounded(random, config.materialDelayMaxMinutes()));
      long due = Math.addExact(Math.max(release, material),
          config.dueSlackMinutes());
      int priority = 1 + random.nextInt(config.priorityLevels());
      result.add(new JobInput(JOB_BASE + index, release, material, due,
          priority, config.operationsPerJob()));
    }
    return result;
  }

  private static List<MachineInput> machines(
      SchedulerConfig config, Random random) {
    List<MachineInput> result =
        new ArrayList<MachineInput>(config.machines());
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
      List<MaintenanceInput> maintenance =
          new ArrayList<MaintenanceInput>();
      if (config.maintenanceDurationMinutes() > 0) {
        long offset = (long) (index + 1)
            * config.maintenancePeriodMinutes() / (config.machines() + 1L);
        long start = Math.addExact(offset,
            config.maintenancePeriodMinutes());
        while (start < horizon) {
          maintenance.add(new MaintenanceInput(start,
              Math.addExact(start, config.maintenanceDurationMinutes())));
          start = Math.addExact(start, config.maintenancePeriodMinutes());
        }
      }
      result.add(new MachineInput(MACHINE_BASE + index, initialFamily,
          initialAvailability, maintenance));
    }
    return result;
  }

  private static List<OperationInput> operations(
      SchedulerConfig config, List<ResourceInput> resources, Random random) {
    List<OperationInput> result =
        new ArrayList<OperationInput>(config.operationCount());
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
        ResourceInput resource =
            resources.get(random.nextInt(resources.size()));
        int maximumUnits = Math.min(resource.capacity,
            config.maxResourceUnits());
        int units = 1 + random.nextInt(maximumUnits);
        long operationId = Math.addExact(
            Math.multiplyExact(jobId, 10_000L), sequence + 1L);
        result.add(new OperationInput(jobId, operationId, sequence,
            FAMILY_BASE + random.nextInt(config.setupFamilies()),
            resource.id, units, options));
      }
    }
    return result;
  }

  private static List<SetupInput> setups(
      SchedulerConfig config, Random random) {
    int count = Math.multiplyExact(config.machines(),
        Math.multiplyExact(config.setupFamilies(), config.setupFamilies()));
    List<SetupInput> result = new ArrayList<SetupInput>(count);
    for (int machine = 0; machine < config.machines(); machine++) {
      for (int from = 0; from < config.setupFamilies(); from++) {
        for (int to = 0; to < config.setupFamilies(); to++) {
          long minutes = from == to ? 0L
              : bounded(random, config.setupMaxMinutes());
          result.add(new SetupInput(MACHINE_BASE + machine,
              FAMILY_BASE + from, FAMILY_BASE + to, minutes));
        }
      }
    }
    return result;
  }

  private static List<TransportInput> transports(
      SchedulerConfig config, Random random) {
    int count = Math.multiplyExact(config.machines(), config.machines());
    List<TransportInput> result = new ArrayList<TransportInput>(count);
    for (int from = 0; from < config.machines(); from++) {
      for (int to = 0; to < config.machines(); to++) {
        long minutes = from == to ? 0L
            : bounded(random, config.transportMaxMinutes());
        result.add(new TransportInput(MACHINE_BASE + from,
            MACHINE_BASE + to, minutes));
      }
    }
    return result;
  }

  private static List<ExternalEvent> events(
      SchedulerConfig config, List<JobInput> jobs, Random random) {
    List<ExternalEvent> result = new ArrayList<ExternalEvent>(
        Math.addExact(Math.multiplyExact(jobs.size(), 2),
            config.machineDelayEvents()));
    for (JobInput job : jobs) {
      result.add(new ExternalEvent(job.releaseMinute,
          SchedulingProblem.JOB_RELEASE, job.id, 0L));
      result.add(new ExternalEvent(job.materialReadyMinute,
          SchedulingProblem.MATERIAL_READY, job.id, 0L));
    }
    for (int index = 0; index < config.machineDelayEvents(); index++) {
      long minute = bounded(random, config.releaseSpreadMinutes());
      long delayedUntil = Math.addExact(minute,
          bounded(random, config.machineDelayMaxMinutes()));
      result.add(new ExternalEvent(minute, SchedulingProblem.MACHINE_DELAY,
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
