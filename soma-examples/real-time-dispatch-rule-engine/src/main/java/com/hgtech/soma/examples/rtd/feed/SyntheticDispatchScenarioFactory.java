package com.hgtech.soma.examples.rtd.feed;

import com.hgtech.soma.examples.rtd.config.DispatchConfig;

/** 只负责生成 detached 测试输入，不持有或修改 live runtime。 */
public final class SyntheticDispatchScenarioFactory
    implements DispatchScenarioFactory {
  @Override
  public DispatchScenario create(DispatchConfig config) {
    if (config == null) throw new NullPointerException("config");
    WorkItem[] initial = new WorkItem[config.initialWork()];
    long nextWorkId = 1L;
    for (int index = 0; index < initial.length; index++) {
      initial[index] = work(
          config, nextWorkId++, 0L, index);
    }
    ResourceSnapshot[] resources =
        new ResourceSnapshot[config.resourceCount()];
    for (int index = 0; index < resources.length; index++) {
      resources[index] = new ResourceSnapshot(
          index + 1L,
          index % config.capabilityCount(),
          0L,
          true);
    }
    DispatchCycle[] cycles =
        new DispatchCycle[config.cycles()];
    for (int cycle = 0; cycle < cycles.length; cycle++) {
      long minute = Math.multiplyExact(
          config.cycleMinutes(), (long) cycle);
      WorkItem[] arrivals =
          new WorkItem[config.arrivalsPerCycle()];
      for (int index = 0; index < arrivals.length; index++) {
        arrivals[index] = work(
            config, nextWorkId++, minute, index);
      }
      cycles[cycle] = new DispatchCycle(
          minute, new WorkArrivalDelta(arrivals));
    }
    return new DispatchScenario(
        config,
        new InitialRuntimeSnapshot(initial, resources),
        cycles);
  }

  private static WorkItem work(
      DispatchConfig config,
      long id,
      long releaseMinute,
      int localOrdinal) {
    long mixed = mix(config.seed() ^ id);
    int capability = (int) Math.floorMod(
        mixed, (long) config.capabilityCount());
    int priority = (int) Math.floorMod(mixed >>> 9, 10L);
    long processing = 1L + Math.floorMod(mixed >>> 17, 8L);
    long slack = 20L + Math.floorMod(
        mixed >>> 27, 41L) + (localOrdinal & 3);
    return new WorkItem(
        id,
        capability,
        releaseMinute,
        Math.addExact(releaseMinute, slack),
        priority,
        processing);
  }

  private static long mix(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }
}
