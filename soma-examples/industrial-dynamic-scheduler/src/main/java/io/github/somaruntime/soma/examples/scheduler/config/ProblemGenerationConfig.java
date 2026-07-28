package io.github.somaruntime.soma.examples.scheduler.config;

import io.github.somaruntime.soma.examples.scheduler.support.StableHash;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/** 只描述 synthetic SchedulingProblem 规模与初始状态的不可变配置。 */
public final class ProblemGenerationConfig {
  private final SortedMap<String, String> values;

  ProblemGenerationConfig(SortedMap<String, String> values) {
    this.values = Collections.unmodifiableSortedMap(
        new TreeMap<String, String>(values));
    validate();
  }

  private void validate() {
    require(intValue("config.version") == 1, "unsupported config.version");
    require(intValue("generator.version") == 1,
        "unsupported generator.version");
    require(longValue("seed") >= 0L, "seed must be non-negative");
    requirePositive("jobs");
    requirePositive("operations.per.job");
    requirePositive("machines");
    requirePositive("candidate.machines.per.operation");
    require(candidateMachinesPerOperation() <= machines(),
        "candidate machine count exceeds machines");
    requirePositive("setup.families");
    requirePositive("secondary.resources");
    requirePositive("secondary.resource.max.capacity");
    requirePositive("secondary.resource.max.units");
    require(maxResourceUnits() <= maxResourceCapacity(),
        "resource units exceed maximum capacity");
    requirePositive("processing.min.minutes");
    require(processingMaxMinutes() >= processingMinMinutes(),
        "processing maximum is smaller than minimum");
    requireNonNegative("setup.max.minutes");
    requireNonNegative("transport.max.minutes");
    requireNonNegative("release.spread.minutes");
    requireNonNegative("material.delay.max.minutes");
    requirePositive("maintenance.period.minutes");
    requireNonNegative("maintenance.duration.minutes");
    require(maintenanceDurationMinutes() < maintenancePeriodMinutes(),
        "maintenance duration must be smaller than period");
    requireNonNegative("machine.delay.events");
    require(machineDelayEvents() <= Math.multiplyExact(machines(), 4),
        "too many machine delay events");
    requireNonNegative("machine.delay.max.minutes");
    requirePositive("due.slack.minutes");
    requirePositive("priority.levels");
    Math.multiplyExact(jobs(), operationsPerJob());
  }

  private void requirePositive(String key) {
    require(intValue(key) > 0, key + " must be positive");
  }

  private void requireNonNegative(String key) {
    require(intValue(key) >= 0, key + " must be non-negative");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private int intValue(String key) {
    try {
      return Integer.parseInt(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(
          key + " must be an integer", invalid);
    }
  }

  private long longValue(String key) {
    try {
      return Long.parseLong(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be a long", invalid);
    }
  }

  public int configVersion() { return intValue("config.version"); }
  public int generatorVersion() { return intValue("generator.version"); }
  public long seed() { return longValue("seed"); }
  public int jobs() { return intValue("jobs"); }
  public int operationsPerJob() { return intValue("operations.per.job"); }
  public int machines() { return intValue("machines"); }
  public int candidateMachinesPerOperation() {
    return intValue("candidate.machines.per.operation");
  }
  public int setupFamilies() { return intValue("setup.families"); }
  public int secondaryResources() { return intValue("secondary.resources"); }
  public int maxResourceCapacity() {
    return intValue("secondary.resource.max.capacity");
  }
  public int maxResourceUnits() {
    return intValue("secondary.resource.max.units");
  }
  public int processingMinMinutes() {
    return intValue("processing.min.minutes");
  }
  public int processingMaxMinutes() {
    return intValue("processing.max.minutes");
  }
  public int setupMaxMinutes() { return intValue("setup.max.minutes"); }
  public int transportMaxMinutes() { return intValue("transport.max.minutes"); }
  public int releaseSpreadMinutes() {
    return intValue("release.spread.minutes");
  }
  public int materialDelayMaxMinutes() {
    return intValue("material.delay.max.minutes");
  }
  public int maintenancePeriodMinutes() {
    return intValue("maintenance.period.minutes");
  }
  public int maintenanceDurationMinutes() {
    return intValue("maintenance.duration.minutes");
  }
  public int machineDelayEvents() { return intValue("machine.delay.events"); }
  public int machineDelayMaxMinutes() {
    return intValue("machine.delay.max.minutes");
  }
  public int dueSlackMinutes() { return intValue("due.slack.minutes"); }
  public int priorityLevels() { return intValue("priority.levels"); }
  public int operationCount() {
    return Math.multiplyExact(jobs(), operationsPerJob());
  }

  public String canonicalText() {
    StringBuilder text = new StringBuilder();
    for (String key : values.keySet()) {
      text.append(key).append('=').append(values.get(key)).append('\n');
    }
    return text.toString();
  }

  public String checksum() {
    return new StableHash().addString(canonicalText()).finishHex();
  }
}
