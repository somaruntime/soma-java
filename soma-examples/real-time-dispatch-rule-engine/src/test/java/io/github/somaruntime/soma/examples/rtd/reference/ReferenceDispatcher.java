package io.github.somaruntime.soma.examples.rtd.reference;

import io.github.somaruntime.soma.examples.rtd.feed.DispatchCycle;
import io.github.somaruntime.soma.examples.rtd.feed.DispatchScenario;
import io.github.somaruntime.soma.examples.rtd.feed.ResourceSnapshot;
import io.github.somaruntime.soma.examples.rtd.feed.WorkItem;
import io.github.somaruntime.soma.examples.rtd.result.DispatchCommand;
import io.github.somaruntime.soma.examples.rtd.result.DispatchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 不依赖 SOMA 的小型业务 reference evaluator。 */
public final class ReferenceDispatcher {
  private static final Comparator<Pair> RULE_ORDER =
      new Comparator<Pair>() {
        @Override
        public int compare(Pair left, Pair right) {
          int compared = Integer.compare(
              right.work.priority, left.work.priority);
          if (compared != 0) return compared;
          compared = Long.compare(
              left.work.dueMinute, right.work.dueMinute);
          if (compared != 0) return compared;
          compared = Long.compare(
              left.work.releaseMinute, right.work.releaseMinute);
          if (compared != 0) return compared;
          compared = Long.compare(left.work.id, right.work.id);
          if (compared != 0) return compared;
          compared = Long.compare(
              left.resource.availableMinute,
              right.resource.availableMinute);
          if (compared != 0) return compared;
          return Long.compare(
              left.resource.id, right.resource.id);
        }
      };

  public DispatchResult dispatch(DispatchScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    Map<Long, Work> work = new HashMap<Long, Work>();
    for (WorkItem item : scenario.initial().work()) {
      work.put(Long.valueOf(item.id()), new Work(item));
    }
    Map<Long, Resource> resources =
        new HashMap<Long, Resource>();
    for (ResourceSnapshot snapshot
        : scenario.initial().resources()) {
      resources.put(
          Long.valueOf(snapshot.id()), new Resource(snapshot));
    }
    ArrayList<DispatchCommand> commands =
        new ArrayList<DispatchCommand>();
    for (DispatchCycle cycle : scenario.cycles()) {
      for (WorkItem item : cycle.arrivals().items()) {
        if (work.put(
            Long.valueOf(item.id()), new Work(item)) != null) {
          throw new IllegalStateException("duplicate reference work");
        }
      }
      ArrayList<Pair> pairs = new ArrayList<Pair>();
      for (Work candidate : work.values()) {
        if (candidate.dispatched
            || candidate.releaseMinute > cycle.currentMinute()) {
          continue;
        }
        for (Resource resource : resources.values()) {
          if (resource.enabled
              && resource.availableMinute <= cycle.currentMinute()
              && resource.capability == candidate.capability) {
            pairs.add(new Pair(candidate, resource));
          }
        }
      }
      Collections.sort(pairs, RULE_ORDER);
      Set<Long> selectedWork = new HashSet<Long>();
      Set<Long> selectedResources = new HashSet<Long>();
      ArrayList<DispatchCommand> cycleCommands =
          new ArrayList<DispatchCommand>();
      for (Pair pair : pairs) {
        if (!selectedWork.add(Long.valueOf(pair.work.id))) continue;
        if (!selectedResources.add(
            Long.valueOf(pair.resource.id))) {
          selectedWork.remove(Long.valueOf(pair.work.id));
          continue;
        }
        long start = Math.max(
            cycle.currentMinute(),
            pair.resource.availableMinute);
        cycleCommands.add(new DispatchCommand(
            pair.work.id,
            pair.resource.id,
            pair.work.capability,
            cycle.currentMinute(),
            start,
            Math.addExact(start, pair.work.processingMinutes),
            pair.work.priority,
            pair.work.version,
            pair.resource.version));
      }
      for (DispatchCommand command : cycleCommands) {
        Work selected = work.get(Long.valueOf(command.workId()));
        Resource assigned =
            resources.get(Long.valueOf(command.resourceId()));
        selected.dispatched = true;
        selected.version = Math.addExact(selected.version, 1L);
        assigned.availableMinute = command.completionMinute();
        assigned.version = Math.addExact(assigned.version, 1L);
      }
      commands.addAll(cycleCommands);
    }
    int dispatched = commands.size();
    return new DispatchResult(
        scenario.cycleCount(),
        work.size(),
        dispatched,
        work.size() - dispatched,
        commands,
        scenario.checksum());
  }

  private static final class Work {
    final long id;
    final int capability;
    final long releaseMinute;
    final long dueMinute;
    final int priority;
    final long processingMinutes;
    long version;
    boolean dispatched;

    Work(WorkItem item) {
      id = item.id();
      capability = item.capability();
      releaseMinute = item.releaseMinute();
      dueMinute = item.dueMinute();
      priority = item.priority();
      processingMinutes = item.processingMinutes();
    }
  }

  private static final class Resource {
    final long id;
    final int capability;
    final boolean enabled;
    long availableMinute;
    long version;

    Resource(ResourceSnapshot snapshot) {
      id = snapshot.id();
      capability = snapshot.capability();
      availableMinute = snapshot.availableMinute();
      enabled = snapshot.enabled();
    }
  }

  private static final class Pair {
    final Work work;
    final Resource resource;

    Pair(Work work, Resource resource) {
      this.work = work;
      this.resource = resource;
    }
  }
}
