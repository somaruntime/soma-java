package com.hgtech.soma.examples.grassing.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** 与领域 Config 分离的 benchmark measurement 参数。 */
final class BenchmarkOptions {
  private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
      "benchmark.warmup",
      "benchmark.forks",
      "benchmark.measurements"));

  private final int warmup;
  private final int forks;
  private final int measurements;

  private BenchmarkOptions(int warmup, int forks, int measurements) {
    if (warmup < 0) {
      throw new IllegalArgumentException(
          "benchmark.warmup must be non-negative");
    }
    if (forks <= 0 || measurements <= 0) {
      throw new IllegalArgumentException(
          "benchmark forks and measurements must be positive");
    }
    this.warmup = warmup;
    this.forks = forks;
    this.measurements = measurements;
  }

  static BenchmarkOptions loadDefault() throws IOException {
    Properties properties = new Properties();
    InputStream input = BenchmarkOptions.class.getClassLoader()
        .getResourceAsStream("benchmark/default.properties");
    if (input == null) {
      throw new IOException("benchmark/default.properties not found");
    }
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    if (!properties.stringPropertyNames().equals(KEYS)) {
      throw new IllegalArgumentException(
          "benchmark keys mismatch: " + properties.stringPropertyNames());
    }
    return new BenchmarkOptions(
        parse(properties, "benchmark.warmup"),
        parse(properties, "benchmark.forks"),
        parse(properties, "benchmark.measurements"));
  }

  private static int parse(Properties properties, String key) {
    try {
      return Integer.parseInt(properties.getProperty(key).trim());
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be an integer", invalid);
    }
  }

  int warmup() { return warmup; }
  int forks() { return forks; }
  int measurements() { return measurements; }
}
