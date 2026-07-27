package com.hgtech.soma.examples.rtd.config;

import com.hgtech.soma.examples.rtd.support.StableHash;

import java.util.TreeMap;

/** 经过完整边界校验的版本化 dispatch 配置。 */
public final class DispatchConfig {
  private static final int MAXIMUM_WORK_ITEMS = 2_000_000;

  private final TreeMap<String, String> values;
  private final int configVersion;
  private final int generatorVersion;
  private final long seed;
  private final int initialWork;
  private final int arrivalsPerCycle;
  private final int resourceCount;
  private final int capabilityCount;
  private final int cycles;
  private final long cycleMinutes;
  private final int workers;
  private final int parallelMinimumCardinality;
  private final long maximumOutputElements;
  private final int maximumTasks;
  private final int totalWork;
  private final String canonicalText;
  private final String checksum;

  DispatchConfig(TreeMap<String, String> values) {
    this.values = new TreeMap<String, String>(values);
    configVersion = parseInt("config.version");
    generatorVersion = parseInt("generator.version");
    seed = parseLong("scenario.seed");
    initialWork = parseInt("work.initial");
    arrivalsPerCycle = parseInt("work.arrivals.per.cycle");
    resourceCount = parseInt("resource.count");
    capabilityCount = parseInt("capability.count");
    cycles = parseInt("dispatch.cycles");
    cycleMinutes = parseLong("dispatch.cycle.minutes");
    workers = parseInt("execution.workers");
    parallelMinimumCardinality =
        parseInt("execution.parallel.minimum.cardinality");
    maximumOutputElements =
        parseLong("budget.maximum.output.elements");
    maximumTasks = parseInt("budget.maximum.tasks");
    totalWork = Math.addExact(
        initialWork, Math.multiplyExact(arrivalsPerCycle, cycles));
    validate();
    canonicalText = canonicalText(this.values);
    checksum = new StableHash().addString(canonicalText).finishHex();
  }

  public DispatchConfig withMaximumOutputElements(long value) {
    TreeMap<String, String> changed =
        new TreeMap<String, String>(values);
    changed.put("budget.maximum.output.elements", Long.toString(value));
    return new DispatchConfig(changed);
  }

  private void validate() {
    require(configVersion == 1, "unsupported config.version");
    require(generatorVersion == 1, "unsupported generator.version");
    require(seed >= 0L, "scenario.seed must be non-negative");
    require(initialWork >= 0, "work.initial must be non-negative");
    require(arrivalsPerCycle >= 0,
        "work.arrivals.per.cycle must be non-negative");
    require(resourceCount > 0, "resource.count must be positive");
    require(capabilityCount > 0,
        "capability.count must be positive");
    require(capabilityCount <= resourceCount,
        "each capability requires at least one resource");
    require(cycles > 0, "dispatch.cycles must be positive");
    require(cycleMinutes > 0L,
        "dispatch.cycle.minutes must be positive");
    Math.multiplyExact(cycleMinutes, cycles - 1L);
    require(totalWork > 0, "scenario must contain work");
    require(totalWork <= MAXIMUM_WORK_ITEMS,
        "workload exceeds reference application boundary");
    require(workers > 0 && workers <= 256,
        "execution.workers must be in [1, 256]");
    require(parallelMinimumCardinality > 0,
        "parallel minimum cardinality must be positive");
    require(maximumOutputElements > 0L,
        "maximum output elements must be positive");
    require(maximumTasks > 0,
        "maximum tasks must be positive");
  }

  private int parseInt(String key) {
    try {
      return Integer.parseInt(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(
          key + " must be an integer", invalid);
    }
  }

  private long parseLong(String key) {
    try {
      return Long.parseLong(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(
          key + " must be a long", invalid);
    }
  }

  private static String canonicalText(TreeMap<String, String> values) {
    StringBuilder text = new StringBuilder();
    for (String key : values.keySet()) {
      text.append(key).append('=').append(values.get(key)).append('\n');
    }
    return text.toString();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public int configVersion() { return configVersion; }
  public int generatorVersion() { return generatorVersion; }
  public long seed() { return seed; }
  public int initialWork() { return initialWork; }
  public int arrivalsPerCycle() { return arrivalsPerCycle; }
  public int resourceCount() { return resourceCount; }
  public int capabilityCount() { return capabilityCount; }
  public int cycles() { return cycles; }
  public long cycleMinutes() { return cycleMinutes; }
  public int workers() { return workers; }
  public int parallelMinimumCardinality() {
    return parallelMinimumCardinality;
  }
  public long maximumOutputElements() {
    return maximumOutputElements;
  }
  public int maximumTasks() { return maximumTasks; }
  public int totalWork() { return totalWork; }
  public String canonicalText() { return canonicalText; }
  public String checksum() { return checksum; }
}
