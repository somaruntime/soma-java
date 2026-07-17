package com.hgtech.soma.benchmarks;

import com.hgtech.soma.examples.fjsp.FcfsSptDispatchRule;
import com.hgtech.soma.examples.fjsp.FjspInstance;
import com.hgtech.soma.examples.fjsp.FjspInstanceFactory;
import com.hgtech.soma.examples.fjsp.FjspProblem;
import com.hgtech.soma.examples.fjsp.FjspSolveResult;
import com.hgtech.soma.examples.fjsp.FjspSolver;
import com.hgtech.soma.examples.fjsp.schema.OperationAssignment;

import java.util.ArrayList;
import java.util.List;

/** 100,000-operation FJSP integrated diagnostic benchmark。 */
public final class FjspScaleBenchmark {
  private FjspScaleBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    FjspBenchmarkOptions options = FjspBenchmarkOptions.parse(args);
    FjspProblem problem = FjspSyntheticProblems.oneHundredThousandOperations(
      options.seed);
    for (int warmup = 0; warmup < options.warmup; warmup++) {
      runOnce(problem, -warmup - 1);
    }

    List<FjspBenchmarkMeasurement> measurements =
      new ArrayList<FjspBenchmarkMeasurement>();
    for (int iteration = 0; iteration < options.measurements; iteration++) {
      measurements.add(runOnce(problem, iteration));
    }
    FjspBenchmarkReport.write(options, problem, measurements);
  }

  private static FjspBenchmarkMeasurement runOnce(
      FjspProblem problem, int iteration) {
    JvmRuntimeMetrics.Snapshot importMetricsStart =
      JvmRuntimeMetrics.snapshot();
    long importStart = System.nanoTime();
    try (FjspInstance instance = FjspInstanceFactory.create(problem)) {
      long importNanos = elapsed(importStart);
      JvmRuntimeMetrics.Delta importMetrics =
        JvmRuntimeMetrics.snapshot().since(importMetricsStart);
      JvmRuntimeMetrics.Snapshot solveMetricsStart =
        JvmRuntimeMetrics.snapshot();
      long solveStart = System.nanoTime();
      FjspSolveResult result = new FjspSolver(
        instance, new FcfsSptDispatchRule()).solve();
      long solveNanos = elapsed(solveStart);
      JvmRuntimeMetrics.Delta solveMetrics =
        JvmRuntimeMetrics.snapshot().since(solveMetricsStart);
      JvmRuntimeMetrics.Snapshot exportMetricsStart =
        JvmRuntimeMetrics.snapshot();
      long exportStart = System.nanoTime();
      List<OperationAssignment> assignments = instance.exportAssignments();
      long exportNanos = elapsed(exportStart);
      JvmRuntimeMetrics.Delta exportMetrics =
        JvmRuntimeMetrics.snapshot().since(exportMetricsStart);
      if (assignments.size() != problem.operationCount()) {
        throw new AssertionError("exported assignment count");
      }
      return new FjspBenchmarkMeasurement(iteration, importNanos, solveNanos,
        exportNanos, importMetrics, solveMetrics, exportMetrics, result,
        instance.runtimePlanHash());
    }
  }

  private static long elapsed(long start) {
    return Math.max(1L, System.nanoTime() - start);
  }
}
