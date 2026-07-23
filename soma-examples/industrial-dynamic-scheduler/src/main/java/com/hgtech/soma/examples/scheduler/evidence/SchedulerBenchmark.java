package com.hgtech.soma.examples.scheduler.evidence;

import com.hgtech.soma.examples.scheduler.config.SchedulerConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemGenerator;
import com.hgtech.soma.examples.scheduler.runtime.IndustrialScheduler;
import com.hgtech.soma.examples.scheduler.runtime.ScheduleResult;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntimeBootstrap;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;
import com.hgtech.soma.examples.scheduler.validation.ScheduleValidator;

import java.util.List;

/** 单 JVM fork 的 correctness-guarded integrated benchmark。 */
public final class SchedulerBenchmark {
  private SchedulerBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    SchedulerConfig config = SchedulerConfig.load(selector);
    SchedulingProblem problem = SchedulingProblemGenerator.generate(config);
    for (int warmup = 0; warmup < config.benchmarkWarmup(); warmup++) {
      execute(problem, false);
    }

    long setupNanos = 0L;
    long solveNanos = 0L;
    long minimumSolveNanos = Long.MAX_VALUE;
    long maximumSolveNanos = 0L;
    long allocatedBytes = 0L;
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
         measurement < config.benchmarkMeasurements(); measurement++) {
      Measurement value = execute(problem, true);
      setupNanos = Math.addExact(setupNanos, value.setupNanos);
      solveNanos = Math.addExact(solveNanos, value.solveNanos);
      minimumSolveNanos = Math.min(minimumSolveNanos, value.solveNanos);
      maximumSolveNanos = Math.max(maximumSolveNanos, value.solveNanos);
      allocatedBytes = Math.addExact(allocatedBytes, value.allocatedBytes);
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
        + "\"artifact\":\"industrial-scheduler-benchmark-v1\","
        + "\"profile\":\"" + selector + "\","
        + "\"inputChecksum\":\"" + problem.checksum() + "\","
        + "\"resultChecksum\":\"" + resultChecksum + "\","
        + "\"schemaHash\":\"" + schemaHash + "\","
        + "\"runtimePlanHash\":\"" + runtimePlanHash + "\","
        + "\"operations\":" + problem.operationCount() + ","
        + "\"warmup\":" + config.benchmarkWarmup() + ","
        + "\"measurements\":" + config.benchmarkMeasurements() + ","
        + "\"setupNanos\":" + setupNanos + ","
        + "\"solveNanos\":" + solveNanos + ","
        + "\"minimumSolveNanos\":" + minimumSolveNanos + ","
        + "\"maximumSolveNanos\":" + maximumSolveNanos + ","
        + "\"allocatedBytes\":" + allocatedBytes + ","
        + "\"youngGcCount\":" + youngGcCount + ","
        + "\"youngGcPauseMillis\":" + youngGcMillis + ","
        + "\"fullGcCount\":" + fullGcCount + ","
        + "\"fullGcPauseMillis\":" + fullGcMillis + ","
        + "\"exactIndexHighWaterBytes\":" + exactIndexHighWater + ","
        + "\"updateScratchHighWaterBytes\":" + updateScratchHighWater + ","
        + "\"operationScratchHighWaterBytes\":"
        + operationScratchHighWater + ","
        + "\"claimAllowed\":false}");
  }

  private static Measurement execute(
      SchedulingProblem problem, boolean measured) {
    long setupStart = System.nanoTime();
    SchedulerRuntime runtime = SchedulerRuntimeBootstrap.load(problem);
    long setupNanos = System.nanoTime() - setupStart;
    try {
      long beforeAllocation = measured
          ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
      JvmMetrics.GcSnapshot beforeGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long solveStart = System.nanoTime();
      ScheduleResult result = new IndustrialScheduler(runtime).solve();
      long solveNanos = System.nanoTime() - solveStart;
      JvmMetrics.GcSnapshot afterGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long allocated = measured
          ? Math.subtractExact(JvmMetrics.currentThreadAllocatedBytes(),
              beforeAllocation) : 0L;
      List<OperationAssignment> assignments = runtime.exportAssignments();
      ScheduleValidator.validate(problem, assignments, result);
      SchedulerRuntime.RuntimeEvidence evidence = runtime.runtimeEvidence();
      return new Measurement(setupNanos, solveNanos, allocated,
          measured ? delta(afterGc.youngCount, beforeGc.youngCount) : 0L,
          measured ? delta(afterGc.youngMillis, beforeGc.youngMillis) : 0L,
          measured ? delta(afterGc.fullCount, beforeGc.fullCount) : 0L,
          measured ? delta(afterGc.fullMillis, beforeGc.fullMillis) : 0L,
          evidence.exactIndexHighWaterBytes,
          evidence.updateScratchHighWaterBytes,
          evidence.operationScratchHighWaterBytes,
          result.resultChecksum, runtime.schemaHash(),
          runtime.runtimePlanHash());
    } finally {
      runtime.close();
    }
  }

  private static long delta(long after, long before) {
    return Math.max(0L, after - before);
  }

  private static final class Measurement {
    final long setupNanos;
    final long solveNanos;
    final long allocatedBytes;
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

    Measurement(long setupNanos, long solveNanos, long allocatedBytes,
                long youngGcCount, long youngGcMillis,
                long fullGcCount, long fullGcMillis,
                long exactIndexHighWater, long updateScratchHighWater,
                long operationScratchHighWater, String resultChecksum,
                String schemaHash, String runtimePlanHash) {
      this.setupNanos = setupNanos;
      this.solveNanos = solveNanos;
      this.allocatedBytes = allocatedBytes;
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
