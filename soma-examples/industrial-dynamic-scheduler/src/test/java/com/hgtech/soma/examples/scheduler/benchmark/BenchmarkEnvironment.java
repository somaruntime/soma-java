package com.hgtech.soma.examples.scheduler.benchmark;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Scheduler benchmark artifact 的环境与 provenance 投影。 */
final class BenchmarkEnvironment {
  final String commit = required("SOMA_BENCHMARK_COMMIT");
  final int fork = positive("SOMA_BENCHMARK_FORK");
  final int configuredForks = positive("SOMA_BENCHMARK_FORKS");
  final String javaVersion = System.getProperty(
      "java.runtime.version", System.getProperty("java.version"));
  final String javaVendor = System.getProperty("java.vendor", "unknown");
  final String javaVmName = System.getProperty("java.vm.name", "unknown");
  final String javaVmVersion = System.getProperty("java.vm.version", "unknown");
  final List<String> jvmArgs = new ArrayList<String>(
      ManagementFactory.getRuntimeMXBean().getInputArguments());
  final String osName = System.getProperty("os.name", "unknown");
  final String osVersion = System.getProperty("os.version", "unknown");
  final String architecture = System.getProperty("os.arch", "unknown");
  final String cpu = required("SOMA_BENCHMARK_CPU");
  final long maxHeapBytes = Runtime.getRuntime().maxMemory();

  BenchmarkEnvironment(int minimumForks) {
    if (fork > configuredForks || configuredForks < minimumForks) {
      throw new IllegalArgumentException("benchmark fork contract mismatch");
    }
  }

  String jsonFields() {
    StringBuilder out = new StringBuilder();
    string(out, "commit", commit);
    number(out, "fork", fork);
    number(out, "configuredForks", configuredForks);
    string(out, "javaVersion", javaVersion);
    string(out, "javaVendor", javaVendor);
    string(out, "javaVmName", javaVmName);
    string(out, "javaVmVersion", javaVmVersion);
    out.append("\"jvmArgs\":[");
    for (int index = 0; index < jvmArgs.size(); index++) {
      if (index != 0) out.append(',');
      quote(out, jvmArgs.get(index));
    }
    out.append("],");
    string(out, "osName", osName);
    string(out, "osVersion", osVersion);
    string(out, "architecture", architecture);
    string(out, "cpu", cpu);
    out.append("\"maxHeapBytes\":").append(maxHeapBytes);
    return out.toString();
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static int positive(String name) {
    int value;
    try {
      value = Integer.parseInt(required(name));
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(name + " must be an integer", failure);
    }
    if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    return value;
  }

  private static void string(StringBuilder out, String name, String value) {
    quote(out, name);
    out.append(':');
    quote(out, value);
    out.append(',');
  }

  private static void number(StringBuilder out, String name, int value) {
    quote(out, name);
    out.append(':').append(value).append(',');
  }

  private static void quote(StringBuilder out, String value) {
    out.append('"');
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      switch (current) {
        case '"': out.append("\\\""); break;
        case '\\': out.append("\\\\"); break;
        case '\b': out.append("\\b"); break;
        case '\f': out.append("\\f"); break;
        case '\n': out.append("\\n"); break;
        case '\r': out.append("\\r"); break;
        case '\t': out.append("\\t"); break;
        default:
          if (current < 0x20) {
            out.append(String.format("\\u%04x", Integer.valueOf(current)));
          } else {
            out.append(current);
          }
      }
    }
    out.append('"');
  }
}
