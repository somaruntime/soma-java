package com.hgtech.soma.examples.rtd.feed;

import com.hgtech.soma.examples.rtd.config.DispatchConfig;
import com.hgtech.soma.examples.rtd.support.StableHash;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 可重放、detached 的 RTD dispatch horizon。 */
public final class DispatchScenario {
  private final DispatchConfig config;
  private final InitialRuntimeSnapshot initial;
  private final DispatchCycle[] cycles;
  private final String checksum;

  public DispatchScenario(
      DispatchConfig config,
      InitialRuntimeSnapshot initial,
      DispatchCycle[] cycles) {
    if (config == null) throw new NullPointerException("config");
    if (initial == null) throw new NullPointerException("initial");
    if (cycles == null) throw new NullPointerException("cycles");
    this.config = config;
    this.initial = initial;
    this.cycles = Arrays.copyOf(cycles, cycles.length);
    validate();
    checksum = computeChecksum();
  }

  private void validate() {
    if (initial.workCount() != config.initialWork()) {
      throw new IllegalArgumentException(
          "initial work count differs from config");
    }
    if (initial.resourceCount() != config.resourceCount()) {
      throw new IllegalArgumentException(
          "resource count differs from config");
    }
    if (cycles.length != config.cycles()) {
      throw new IllegalArgumentException(
          "cycle count differs from config");
    }
    boolean[] capabilityCovered =
        new boolean[config.capabilityCount()];
    Set<Long> resourceIds = new HashSet<Long>();
    for (ResourceSnapshot resource : initial.resources()) {
      requireCapability(resource.capability());
      if (!resourceIds.add(Long.valueOf(resource.id()))) {
        throw new IllegalArgumentException(
            "duplicate resource id: " + resource.id());
      }
      if (resource.enabled()) {
        capabilityCovered[resource.capability()] = true;
      }
    }
    for (int capability = 0;
         capability < capabilityCovered.length; capability++) {
      if (!capabilityCovered[capability]) {
        throw new IllegalArgumentException(
            "capability lacks enabled resource: " + capability);
      }
    }
    Set<Long> workIds = new HashSet<Long>();
    for (WorkItem item : initial.work()) {
      validateWork(item, workIds);
    }
    long previous = -1L;
    int arrivals = 0;
    for (DispatchCycle cycle : cycles) {
      if (cycle == null) throw new NullPointerException("cycle");
      if (cycle.currentMinute() <= previous) {
        throw new IllegalArgumentException(
            "cycle minutes must be strictly increasing");
      }
      previous = cycle.currentMinute();
      for (WorkItem item : cycle.arrivals().items()) {
        validateWork(item, workIds);
        arrivals = Math.addExact(arrivals, 1);
      }
    }
    if (arrivals != Math.multiplyExact(
        config.arrivalsPerCycle(), config.cycles())) {
      throw new IllegalArgumentException(
          "arrival count differs from config");
    }
    if (workIds.size() != config.totalWork()) {
      throw new IllegalArgumentException(
          "total work identity count differs from config");
    }
  }

  private void validateWork(WorkItem item, Set<Long> identities) {
    requireCapability(item.capability());
    if (!identities.add(Long.valueOf(item.id()))) {
      throw new IllegalArgumentException(
          "duplicate work id: " + item.id());
    }
  }

  private void requireCapability(int capability) {
    if (capability < 0 || capability >= config.capabilityCount()) {
      throw new IllegalArgumentException(
          "capability outside configured range: " + capability);
    }
  }

  private String computeChecksum() {
    StableHash hash = new StableHash()
        .addString("rtd-dispatch-scenario-v1")
        .addString(config.checksum());
    hash.addInt(initial.workCount());
    for (WorkItem item : initial.work()) addWork(hash, item);
    hash.addInt(initial.resourceCount());
    for (ResourceSnapshot resource : initial.resources()) {
      hash.addLong(resource.id())
          .addInt(resource.capability())
          .addLong(resource.availableMinute())
          .addBoolean(resource.enabled());
    }
    hash.addInt(cycles.length);
    for (DispatchCycle cycle : cycles) {
      hash.addLong(cycle.currentMinute())
          .addInt(cycle.arrivals().size());
      for (WorkItem item : cycle.arrivals().items()) addWork(hash, item);
    }
    return hash.finishHex();
  }

  private static void addWork(StableHash hash, WorkItem item) {
    hash.addLong(item.id())
        .addInt(item.capability())
        .addLong(item.releaseMinute())
        .addLong(item.dueMinute())
        .addInt(item.priority())
        .addLong(item.processingMinutes());
  }

  public DispatchConfig config() { return config; }
  public InitialRuntimeSnapshot initial() { return initial; }
  public int cycleCount() { return cycles.length; }
  public DispatchCycle cycleAt(int index) { return cycles[index]; }
  public DispatchCycle[] cycles() {
    return Arrays.copyOf(cycles, cycles.length);
  }
  public String checksum() { return checksum; }
}
