package com.hgtech.soma.examples.scheduler.config;

import com.hgtech.soma.examples.scheduler.support.StableHash;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** 严格、版本化且可输出最终生效值的调度应用配置。 */
public final class SchedulerConfig {
  private static final Set<String> KEYS = Collections.unmodifiableSet(
      new HashSet<String>(Arrays.asList(
          "config.version", "generator.version", "seed", "jobs",
          "operations.per.job", "machines", "candidate.machines.per.operation",
          "setup.families", "secondary.resources",
          "secondary.resource.max.capacity", "secondary.resource.max.units",
          "processing.min.minutes", "processing.max.minutes",
          "setup.max.minutes", "transport.max.minutes",
          "release.spread.minutes", "material.delay.max.minutes",
          "maintenance.period.minutes", "maintenance.duration.minutes",
          "machine.delay.events", "machine.delay.max.minutes",
          "due.slack.minutes", "priority.levels", "benchmark.warmup",
          "benchmark.forks", "benchmark.measurements")));

  private final TreeMap<String, String> values;

  private SchedulerConfig(TreeMap<String, String> values) {
    this.values = values;
    validate();
  }

  public static SchedulerConfig load(String selector, String... overrides)
      throws IOException {
    if (selector == null || selector.trim().isEmpty()) {
      throw new IllegalArgumentException("config selector must not be empty");
    }
    Properties properties = new Properties();
    InputStream input = open(selector.trim());
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    TreeMap<String, String> effective = new TreeMap<String, String>();
    for (String key : properties.stringPropertyNames()) {
      effective.put(key, properties.getProperty(key).trim());
    }
    if (overrides != null) {
      for (String override : overrides) {
        int separator = override == null ? -1 : override.indexOf('=');
        if (separator <= 0 || separator == override.length() - 1) {
          throw new IllegalArgumentException(
              "override must use key=value: " + override);
        }
        effective.put(override.substring(0, separator).trim(),
            override.substring(separator + 1).trim());
      }
    }
    if (!effective.keySet().equals(KEYS)) {
      Set<String> missing = new HashSet<String>(KEYS);
      missing.removeAll(effective.keySet());
      Set<String> unknown = new HashSet<String>(effective.keySet());
      unknown.removeAll(KEYS);
      throw new IllegalArgumentException(
          "config keys mismatch; missing=" + missing + ", unknown=" + unknown);
    }
    return new SchedulerConfig(effective);
  }

  private static InputStream open(String selector) throws IOException {
    File file = new File(selector);
    if (file.isFile()) {
      return new FileInputStream(file);
    }
    String resource = selector.endsWith(".properties")
        ? (selector.startsWith("config/") ? selector : "config/" + selector)
        : "config/" + selector + ".properties";
    InputStream input = SchedulerConfig.class.getClassLoader()
        .getResourceAsStream(resource);
    if (input == null) {
      throw new IOException("config not found as file or resource: " + selector);
    }
    return input;
  }

  private void validate() {
    require(intValue("config.version") == 1, "unsupported config.version");
    require(intValue("generator.version") == 1, "unsupported generator.version");
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
    requireNonNegative("benchmark.warmup");
    requirePositive("benchmark.forks");
    requirePositive("benchmark.measurements");
    Math.multiplyExact(jobs(), operationsPerJob());
  }

  private void requirePositive(String key) {
    require(intValue(key) > 0, key + " must be positive");
  }

  private void requireNonNegative(String key) {
    require(intValue(key) >= 0, key + " must be non-negative");
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }

  private int intValue(String key) {
    try {
      return Integer.parseInt(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be an integer", invalid);
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
  public int benchmarkWarmup() { return intValue("benchmark.warmup"); }
  public int benchmarkForks() { return intValue("benchmark.forks"); }
  public int benchmarkMeasurements() {
    return intValue("benchmark.measurements");
  }
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
