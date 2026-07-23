package com.hgtech.soma.examples.scheduler.benchmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** 与 Problem generation 独立的 benchmark measurement 配置。 */
public final class BenchmarkOptions {
  private static final Set<String> KEYS = new HashSet<String>(
      Arrays.asList("benchmark.warmup", "benchmark.forks",
          "benchmark.measurements"));

  private final int warmup;
  private final int forks;
  private final int measurements;

  private BenchmarkOptions(Properties values) {
    require(values.stringPropertyNames().equals(KEYS),
        "benchmark keys mismatch");
    warmup = intValue(values, "benchmark.warmup");
    forks = intValue(values, "benchmark.forks");
    measurements = intValue(values, "benchmark.measurements");
    require(warmup >= 0, "benchmark.warmup must be non-negative");
    require(forks > 0, "benchmark.forks must be positive");
    require(measurements > 0,
        "benchmark.measurements must be positive");
  }

  public static BenchmarkOptions load(String selector) throws IOException {
    if (selector == null || selector.trim().isEmpty()) {
      throw new IllegalArgumentException(
          "benchmark selector must not be empty");
    }
    Properties properties = new Properties();
    InputStream input = open(selector.trim());
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    return new BenchmarkOptions(properties);
  }

  private static InputStream open(String selector) throws IOException {
    File file = new File(selector);
    if (file.isFile()) return new FileInputStream(file);
    String resource = selector.endsWith(".properties")
        ? (selector.startsWith("benchmark/") ? selector
            : "benchmark/" + selector)
        : "benchmark/" + selector + ".properties";
    InputStream input = BenchmarkOptions.class.getClassLoader()
        .getResourceAsStream(resource);
    if (input == null) {
      throw new IOException("benchmark options not found: " + selector);
    }
    return input;
  }

  private static int intValue(Properties values, String key) {
    try {
      return Integer.parseInt(values.getProperty(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(
          key + " must be an integer", invalid);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public int warmup() { return warmup; }
  public int forks() { return forks; }
  public int measurements() { return measurements; }
}
