package com.hgtech.soma.benchmarks;

import com.hgtech.soma.examples.fjsp.FjspProblem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** JSONL artifact 与人类可读摘要输出。 */
final class FjspBenchmarkReport {
  private FjspBenchmarkReport() {
  }

  static void write(FjspBenchmarkOptions options, FjspProblem problem,
                    List<FjspBenchmarkMeasurement> measurements)
      throws IOException {
    File parent = options.output.getAbsoluteFile().getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("cannot create benchmark output directory: " + parent);
    }
    FileWriter writer = new FileWriter(options.output);
    try {
      for (FjspBenchmarkMeasurement measurement : measurements) {
        writer.write(measurement.json(problem, options));
        writer.write('\n');
      }
    } finally {
      writer.close();
    }
    printSummary(options.output, problem, measurements);
  }

  private static void printSummary(
      File output, FjspProblem problem,
      List<FjspBenchmarkMeasurement> measurements) {
    List<Long> solveNanos = new ArrayList<Long>();
    for (FjspBenchmarkMeasurement measurement : measurements) {
      solveNanos.add(Long.valueOf(measurement.solveNanos));
    }
    Collections.sort(solveNanos);
    long median = solveNanos.get(solveNanos.size() / 2).longValue();
    FjspBenchmarkMeasurement latest =
      measurements.get(measurements.size() - 1);
    long allocatedBytes = latest.importMetrics.allocatedBytes < 0L
      || latest.solveMetrics.allocatedBytes < 0L
      || latest.exportMetrics.allocatedBytes < 0L
      ? -1L : latest.importMetrics.allocatedBytes
      + latest.solveMetrics.allocatedBytes
      + latest.exportMetrics.allocatedBytes;
    System.out.println("fjsp-benchmark-scenario: 1000 jobs x 100 operations, "
      + "100 machines, 3 candidates/operation, FCFS+SPT");
    System.out.println("fjsp-benchmark-solve-median-ms: "
      + median / 1000000.0d);
    System.out.println("fjsp-benchmark-throughput-operations-per-second: "
      + (problem.operationCount() * 1000000000.0d / median));
    System.out.println("fjsp-benchmark-allocated-bytes-per-operation: "
      + (allocatedBytes < 0L ? "not-observed"
      : Double.toString((double) allocatedBytes / problem.operationCount())));
    System.out.println("fjsp-benchmark-total-allocated-bytes: "
      + (allocatedBytes < 0L ? "not-observed" : Long.toString(allocatedBytes)));
    System.out.println("fjsp-benchmark-young-gc-count: "
      + (latest.importMetrics.youngGcCount + latest.solveMetrics.youngGcCount
      + latest.exportMetrics.youngGcCount));
    System.out.println("fjsp-benchmark-young-gc-time-ms: "
      + (latest.importMetrics.youngGcTimeMillis
      + latest.solveMetrics.youngGcTimeMillis
      + latest.exportMetrics.youngGcTimeMillis));
    System.out.println("fjsp-benchmark-full-gc-count: "
      + (latest.importMetrics.fullGcCount + latest.solveMetrics.fullGcCount
      + latest.exportMetrics.fullGcCount));
    System.out.println("fjsp-benchmark-full-gc-time-ms: "
      + (latest.importMetrics.fullGcTimeMillis
      + latest.solveMetrics.fullGcTimeMillis
      + latest.exportMetrics.fullGcTimeMillis));
    System.out.println("fjsp-benchmark-artifact: " + output.getAbsolutePath());
    System.out.println("fjsp-benchmark-claim-allowed: false");
  }
}
