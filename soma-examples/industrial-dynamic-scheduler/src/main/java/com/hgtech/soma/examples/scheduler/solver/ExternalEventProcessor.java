package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.problem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.ExternalEventType;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.OperationKey;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.HashSet;
import java.util.Set;

/** 外部事件回放与首工序发布的唯一 Owner。 */
final class ExternalEventProcessor {
  private final SchedulerRuntime runtime;
  private final CandidateFrontier frontier;
  private final Set<Long> affectedJobs = new HashSet<Long>();
  private long processedEvents;

  ExternalEventProcessor(
      SchedulerRuntime runtime, CandidateFrontier frontier) {
    this.runtime = runtime;
    this.frontier = frontier;
  }

  boolean hasPending() {
    return runtime.hasEvents();
  }

  long nextMinute() {
    return runtime.nextEventMinute();
  }

  long processedEvents() {
    return processedEvents;
  }

  void processThrough(long cutoff) {
    affectedJobs.clear();
    while (runtime.hasEvents()
        && runtime.nextEventMinute() <= cutoff) {
      ExternalEvent event = runtime.pollEvent();
      processedEvents = Math.addExact(processedEvents, 1L);
      if (event.type == ExternalEventType.JOB_RELEASE) {
        runtime.markJobReleased(event.subjectId);
        affectedJobs.add(Long.valueOf(event.subjectId));
      } else if (event.type == ExternalEventType.MATERIAL_READY) {
        runtime.markMaterialReady(event.subjectId);
        affectedJobs.add(Long.valueOf(event.subjectId));
      } else if (event.type == ExternalEventType.MACHINE_DELAY) {
        applyMachineDelay(event);
      } else {
        throw new IllegalStateException(
            "unknown runtime event " + event.type);
      }
    }
    for (Long jobId : affectedJobs) {
      if (runtime.readyToPublish(jobId.longValue())) {
        OperationKey first =
            frontier.operationAt(jobId.longValue(), 0);
        frontier.releaseOperation(first, 0L, null);
        runtime.markInitialOperationPublished(jobId.longValue());
      }
    }
  }

  private void applyMachineDelay(ExternalEvent event) {
    MachineId machine = new MachineId(event.subjectId);
    int index = runtime.machineStates().requireIndex(machine);
    LongColumnView availability =
        runtime.machineStates().nextAvailableMinuteColumn();
    LongColumnView versions =
        runtime.machineStates().versionColumn();
    long currentAvailability;
    long version;
    try {
      currentAvailability = availability.getLong(index);
      version = versions.getLong(index);
    } finally {
      versions.close();
      availability.close();
    }
    runtime.machineStates().mutate(machine)
        .setNextAvailableMinute(
            Math.max(currentAvailability, event.value))
        .setVersion(Math.addExact(version, 1L))
        .commit();
    frontier.refreshMachine(machine);
  }
}
