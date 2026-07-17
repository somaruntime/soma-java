package com.hgtech.soma.benchmarks;

import com.hgtech.soma.examples.fjsp.FjspProblem;
import com.hgtech.soma.examples.fjsp.FjspSolveResult;

import java.lang.management.ManagementFactory;

/** 单次 measurement 与 JSONL 序列化。 */
final class FjspBenchmarkMeasurement {
  final int iteration;
  final long importNanos;
  final long solveNanos;
  final long exportNanos;
  final JvmRuntimeMetrics.Delta importMetrics;
  final JvmRuntimeMetrics.Delta solveMetrics;
  final JvmRuntimeMetrics.Delta exportMetrics;
  final FjspSolveResult result;
  final String runtimePlanHash;

  FjspBenchmarkMeasurement(int iteration, long importNanos, long solveNanos,
                           long exportNanos,
                           JvmRuntimeMetrics.Delta importMetrics,
                           JvmRuntimeMetrics.Delta solveMetrics,
                           JvmRuntimeMetrics.Delta exportMetrics,
                           FjspSolveResult result,
                           String runtimePlanHash) {
    this.iteration = iteration;
    this.importNanos = importNanos;
    this.solveNanos = solveNanos;
    this.exportNanos = exportNanos;
    this.importMetrics = importMetrics;
    this.solveMetrics = solveMetrics;
    this.exportMetrics = exportMetrics;
    this.result = result;
    this.runtimePlanHash = runtimePlanHash;
  }

  String json(FjspProblem problem, FjspBenchmarkOptions options) {
    long totalAllocatedBytes = sumAllocatedBytes();
    long youngGcCount = importMetrics.youngGcCount
      + solveMetrics.youngGcCount + exportMetrics.youngGcCount;
    long youngGcTimeMillis = importMetrics.youngGcTimeMillis
      + solveMetrics.youngGcTimeMillis + exportMetrics.youngGcTimeMillis;
    long fullGcCount = importMetrics.fullGcCount
      + solveMetrics.fullGcCount + exportMetrics.fullGcCount;
    long fullGcTimeMillis = importMetrics.fullGcTimeMillis
      + solveMetrics.fullGcTimeMillis + exportMetrics.fullGcTimeMillis;
    long unknownGcCount = importMetrics.unknownGcCount
      + solveMetrics.unknownGcCount + exportMetrics.unknownGcCount;
    long unknownGcTimeMillis = importMetrics.unknownGcTimeMillis
      + solveMetrics.unknownGcTimeMillis + exportMetrics.unknownGcTimeMillis;
    return "{"
      + "\"schemaVersion\":\"soma-fjsp-benchmark-v2\","
      + "\"scenario\":\"fjsp.solve.fcfs_spt_100k\","
      + "\"claimAllowed\":false,"
      + "\"iteration\":" + iteration + ","
      + "\"warmupIterations\":" + options.warmup + ","
      + "\"measurementIterations\":" + options.measurements + ","
      + "\"seed\":" + options.seed + ","
      + "\"jobs\":" + problem.jobCount() + ","
      + "\"operationsPerJob\":"
      + (problem.operationCount() / problem.jobCount()) + ","
      + "\"operations\":" + problem.operationCount() + ","
      + "\"machines\":" + problem.machineCount() + ","
      + "\"candidateMachinesPerOperation\":"
      + (problem.candidateCount() / problem.operationCount()) + ","
      + "\"candidateMachines\":" + problem.candidateCount() + ","
      + "\"dispatchRule\":\"effective-ready,fcfs,spt,identity\","
      + "\"importNanos\":" + importNanos + ","
      + "\"solveNanos\":" + solveNanos + ","
      + "\"exportNanos\":" + exportNanos + ","
      + "\"allocationMethod\":"
      + quote(JvmRuntimeMetrics.allocationMethod()) + ","
      + "\"importAllocatedBytes\":"
      + nullableLong(importMetrics.allocatedBytes) + ","
      + "\"solveAllocatedBytes\":"
      + nullableLong(solveMetrics.allocatedBytes) + ","
      + "\"exportAllocatedBytes\":"
      + nullableLong(exportMetrics.allocatedBytes) + ","
      + "\"totalAllocatedBytes\":"
      + nullableLong(totalAllocatedBytes) + ","
      + "\"allocatedBytesPerOperation\":"
      + nullableRatio(totalAllocatedBytes, problem.operationCount()) + ","
      + "\"youngGcCount\":" + youngGcCount + ","
      + "\"youngGcTimeMillis\":" + youngGcTimeMillis + ","
      + "\"fullGcCount\":" + fullGcCount + ","
      + "\"fullGcTimeMillis\":" + fullGcTimeMillis + ","
      + "\"unknownGcCount\":" + unknownGcCount + ","
      + "\"unknownGcTimeMillis\":" + unknownGcTimeMillis + ","
      + "\"gcCollectorNames\":"
      + quote(JvmRuntimeMetrics.collectorNamesValue()) + ","
      + "\"assignments\":" + result.assignments + ","
      + "\"completedJobs\":" + result.completedJobs + ","
      + "\"makespan\":" + result.makespan + ","
      + "\"totalTardiness\":" + result.totalTardiness + ","
      + "\"checksum\":" + result.checksum + ","
      + "\"runtimePlanHash\":" + quote(runtimePlanHash) + ","
      + "\"javaVersion\":" + quote(System.getProperty("java.version")) + ","
      + "\"javaVendor\":" + quote(System.getProperty("java.vendor")) + ","
      + "\"jvmArgs\":" + quote(ManagementFactory.getRuntimeMXBean()
      .getInputArguments().toString()) + ","
      + "\"osName\":" + quote(System.getProperty("os.name")) + ","
      + "\"osVersion\":" + quote(System.getProperty("os.version")) + ","
      + "\"architecture\":" + quote(System.getProperty("os.arch"))
      + "}";
  }

  private long sumAllocatedBytes() {
    if (importMetrics.allocatedBytes < 0L || solveMetrics.allocatedBytes < 0L
        || exportMetrics.allocatedBytes < 0L) {
      return -1L;
    }
    return importMetrics.allocatedBytes + solveMetrics.allocatedBytes
      + exportMetrics.allocatedBytes;
  }

  private static String nullableLong(long value) {
    return value < 0L ? "null" : Long.toString(value);
  }

  private static String nullableRatio(long numerator, long denominator) {
    return numerator < 0L
      ? "null" : Double.toString((double) numerator / denominator);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
