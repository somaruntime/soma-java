package io.github.somaruntime.soma.examples.scheduler.benchmark;

import io.github.somaruntime.soma.examples.scheduler.config.ProblemConfigLoader;
import io.github.somaruntime.soma.examples.scheduler.config.ProblemGenerationConfig;
import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblem;
import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblemFactory;
import io.github.somaruntime.soma.examples.scheduler.problem.SyntheticSchedulingProblemFactory;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleResult;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleValidator;
import io.github.somaruntime.soma.examples.scheduler.solver.SchedulerExecutionTestAccess;
import io.github.somaruntime.soma.examples.scheduler.solver.SchedulingSession;
import io.github.somaruntime.soma.examples.scheduler.solver.SchedulingSolver;
import io.github.somaruntime.soma.examples.scheduler.solver.SomaSchedulingSolver;

/** 单 JVM fork 的 correctness-guarded integrated benchmark。 */
public final class SchedulerBenchmark {
  private SchedulerBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    ProblemGenerationConfig config = ProblemConfigLoader.load(selector);
    BenchmarkOptions options = BenchmarkOptions.load(
        args.length < 2 ? "default" : args[1]);
    BenchmarkEnvironment environment =
        new BenchmarkEnvironment(options.forks());
    SchedulingProblemFactory factory =
        new SyntheticSchedulingProblemFactory();
    SchedulingProblem problem = factory.create(config);
    for (int warmup = 0; warmup < options.warmup(); warmup++) {
      execute(problem, false);
    }

    long preparationNanos = 0L;
    long solveNanos = 0L;
    long endToEndNanos = 0L;
    long minimumSolveNanos = Long.MAX_VALUE;
    long maximumSolveNanos = 0L;
    long allocatedBytes = 0L;
    long endToEndAllocatedBytes = 0L;
    long youngGcCount = 0L;
    long youngGcMillis = 0L;
    long fullGcCount = 0L;
    long fullGcMillis = 0L;
    long exactIndexHighWater = 0L;
    long updateScratchHighWater = 0L;
    long operationScratchHighWater = 0L;
    String resultChecksum = null;
    String schemaHash = null;
    String runtimePlanHash = null;
    for (int measurement = 0;
         measurement < options.measurements(); measurement++) {
      Measurement value = execute(problem, true);
      preparationNanos = Math.addExact(
          preparationNanos, value.preparationNanos);
      solveNanos = Math.addExact(solveNanos, value.solveNanos);
      endToEndNanos = Math.addExact(
          endToEndNanos, value.endToEndNanos);
      minimumSolveNanos = Math.min(minimumSolveNanos, value.solveNanos);
      maximumSolveNanos = Math.max(maximumSolveNanos, value.solveNanos);
      allocatedBytes = Math.addExact(allocatedBytes, value.allocatedBytes);
      endToEndAllocatedBytes = Math.addExact(
          endToEndAllocatedBytes, value.endToEndAllocatedBytes);
      youngGcCount = Math.addExact(youngGcCount, value.youngGcCount);
      youngGcMillis = Math.addExact(youngGcMillis, value.youngGcMillis);
      fullGcCount = Math.addExact(fullGcCount, value.fullGcCount);
      fullGcMillis = Math.addExact(fullGcMillis, value.fullGcMillis);
      exactIndexHighWater = Math.max(
          exactIndexHighWater, value.exactIndexHighWater);
      updateScratchHighWater = Math.max(
          updateScratchHighWater, value.updateScratchHighWater);
      operationScratchHighWater = Math.max(
          operationScratchHighWater, value.operationScratchHighWater);
      if (resultChecksum == null) {
        resultChecksum = value.resultChecksum;
        schemaHash = value.schemaHash;
        runtimePlanHash = value.runtimePlanHash;
      } else if (!resultChecksum.equals(value.resultChecksum)
          || !schemaHash.equals(value.schemaHash)
          || !runtimePlanHash.equals(value.runtimePlanHash)) {
        throw new IllegalStateException(
            "benchmark measurement changed result or runtime identity");
      }
    }
    System.out.println("{"
        + "\"schemaVersion\":\"soma-reference-application-benchmark-v1\","
        + "\"artifactVersion\":\"industrial-scheduler-benchmark-v4\","
        + environment.jsonFields() + ","
        + "\"profile\":\"" + selector + "\","
        + "\"configChecksum\":\"" + config.checksum() + "\","
        + "\"generatorVersion\":" + config.generatorVersion() + ","
        + "\"seed\":" + config.seed() + ","
        + "\"inputChecksum\":\"" + problem.checksum() + "\","
        + "\"resultChecksum\":\"" + resultChecksum + "\","
        + "\"schemaHash\":\"" + schemaHash + "\","
        + "\"runtimePlanHash\":\"" + runtimePlanHash + "\","
        + "\"jobs\":" + problem.jobs().size() + ","
        + "\"operations\":" + problem.operationCount() + ","
        + "\"machines\":" + problem.machines().size() + ","
        + "\"candidatesPerOperation\":"
        + problem.maximumCandidatesPerOperation() + ","
        + "\"warmup\":" + options.warmup() + ","
        + "\"measurements\":" + options.measurements() + ","
        + "\"operationExecutions\":"
        + Math.multiplyExact(
            problem.operationCount(), options.measurements()) + ","
        + "\"preparationNanos\":" + preparationNanos + ","
        + "\"solveNanos\":" + solveNanos + ","
        + "\"endToEndNanos\":" + endToEndNanos + ","
        + "\"minimumSolveNanos\":" + minimumSolveNanos + ","
        + "\"maximumSolveNanos\":" + maximumSolveNanos + ","
        + "\"solveNanosPerOperation\":"
        + ceilingDivide(solveNanos, Math.multiplyExact(
            problem.operationCount(), options.measurements())) + ","
        + "\"allocatedBytes\":" + allocatedBytes + ","
        + "\"allocatedBytesPerOperation\":"
        + ceilingDivide(allocatedBytes, Math.multiplyExact(
            problem.operationCount(), options.measurements())) + ","
        + "\"endToEndAllocatedBytes\":"
        + endToEndAllocatedBytes + ","
        + "\"endToEndAllocatedBytesPerOperation\":"
        + ceilingDivide(endToEndAllocatedBytes, Math.multiplyExact(
            problem.operationCount(), options.measurements())) + ","
        + "\"youngGcCount\":" + youngGcCount + ","
        + "\"youngGcPauseMillis\":" + youngGcMillis + ","
        + "\"fullGcCount\":" + fullGcCount + ","
        + "\"fullGcPauseMillis\":" + fullGcMillis + ","
        + "\"exactIndexHighWaterBytes\":" + exactIndexHighWater + ","
        + "\"updateScratchHighWaterBytes\":" + updateScratchHighWater + ","
        + "\"operationScratchHighWaterBytes\":"
        + operationScratchHighWater + ","
        + "\"frontierCapacity\":"
        + problem.frontierCapacity() + ","
        + "\"claimAllowed\":false}");
  }

  private static long ceilingDivide(long value, int divisor) {
    if (value < 0L || divisor <= 0) {
      throw new IllegalArgumentException(
          "ceiling division requires non-negative value and positive divisor");
    }
    return value == 0L ? 0L : 1L + (value - 1L) / divisor;
  }

  private static Measurement execute(
      SchedulingProblem problem, boolean measured) {
    long beforeEndToEndAllocation = measured
        ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
    long preparationStart = System.nanoTime();
    SchedulingSolver solver = new SomaSchedulingSolver();
    SchedulingSession session = solver.prepare(problem);
    long preparationNanos = System.nanoTime() - preparationStart;
    try {
      long beforeAllocation = measured
          ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
      JvmMetrics.GcSnapshot beforeGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long solveStart = System.nanoTime();
      ScheduleResult result = session.solve();
      long solveNanos = System.nanoTime() - solveStart;
      long endToEndNanos = Math.addExact(
          preparationNanos, solveNanos);
      JvmMetrics.GcSnapshot afterGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long afterAllocation = measured
          ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
      long allocated = measured ? Math.subtractExact(
          afterAllocation, beforeAllocation) : 0L;
      long endToEndAllocated = measured ? Math.subtractExact(
          afterAllocation, beforeEndToEndAllocation) : 0L;
      ScheduleValidator.validate(problem, result);
      SchedulerExecutionTestAccess.Evidence evidence =
          SchedulerExecutionTestAccess.capture(session);
      return new Measurement(
          preparationNanos, solveNanos, endToEndNanos,
          allocated, endToEndAllocated,
          measured ? delta(afterGc.youngCount, beforeGc.youngCount) : 0L,
          measured ? delta(afterGc.youngMillis, beforeGc.youngMillis) : 0L,
          measured ? delta(afterGc.fullCount, beforeGc.fullCount) : 0L,
          measured ? delta(afterGc.fullMillis, beforeGc.fullMillis) : 0L,
          evidence.exactIndexHighWaterBytes,
          evidence.updateScratchHighWaterBytes,
          evidence.operationScratchHighWaterBytes,
          result.resultChecksum, evidence.schemaHash,
          evidence.runtimePlanHash);
    } finally {
      session.close();
    }
  }

  private static long delta(long after, long before) {
    return Math.max(0L, after - before);
  }

  private static final class Measurement {
    final long preparationNanos;
    final long solveNanos;
    final long endToEndNanos;
    final long allocatedBytes;
    final long endToEndAllocatedBytes;
    final long youngGcCount;
    final long youngGcMillis;
    final long fullGcCount;
    final long fullGcMillis;
    final long exactIndexHighWater;
    final long updateScratchHighWater;
    final long operationScratchHighWater;
    final String resultChecksum;
    final String schemaHash;
    final String runtimePlanHash;

    Measurement(
                long preparationNanos, long solveNanos,
                long endToEndNanos, long allocatedBytes,
                long endToEndAllocatedBytes,
                long youngGcCount, long youngGcMillis,
                long fullGcCount, long fullGcMillis,
                long exactIndexHighWater, long updateScratchHighWater,
                long operationScratchHighWater, String resultChecksum,
                String schemaHash, String runtimePlanHash) {
      this.preparationNanos = preparationNanos;
      this.solveNanos = solveNanos;
      this.endToEndNanos = endToEndNanos;
      this.allocatedBytes = allocatedBytes;
      this.endToEndAllocatedBytes = endToEndAllocatedBytes;
      this.youngGcCount = youngGcCount;
      this.youngGcMillis = youngGcMillis;
      this.fullGcCount = fullGcCount;
      this.fullGcMillis = fullGcMillis;
      this.exactIndexHighWater = exactIndexHighWater;
      this.updateScratchHighWater = updateScratchHighWater;
      this.operationScratchHighWater = operationScratchHighWater;
      this.resultChecksum = resultChecksum;
      this.schemaHash = schemaHash;
      this.runtimePlanHash = runtimePlanHash;
    }
  }
}
