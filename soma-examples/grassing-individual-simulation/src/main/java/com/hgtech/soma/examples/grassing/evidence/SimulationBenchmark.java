package com.hgtech.soma.examples.grassing.evidence;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.config.SimulationConfigLoader;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationEngine;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntimeFactory;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.scenario.SyntheticSimulationScenarioFactory;
import com.hgtech.soma.examples.grassing.validation.SimulationValidator;

/** 单 JVM fork 的 correctness-guarded integrated benchmark。 */
public final class SimulationBenchmark {
  private SimulationBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    SimulationConfig config = new SimulationConfigLoader().load(selector);
    SimulationScenario initialState =
        new SyntheticSimulationScenarioFactory().create(config);
    for (int warmup = 0; warmup < config.benchmarkWarmup(); warmup++) {
      execute(config, initialState, false);
    }

    long setupNanos = 0L;
    long tickNanos = 0L;
    long minimumTickNanos = Long.MAX_VALUE;
    long maximumTickNanos = 0L;
    long allocatedBytes = 0L;
    long youngGcCount = 0L;
    long youngGcMillis = 0L;
    long fullGcCount = 0L;
    long fullGcMillis = 0L;
    long exactIndexHighWater = 0L;
    long updateScratchHighWater = 0L;
    long operationScratchHighWater = 0L;
    long populationGrowthCount = 0L;
    int maximumPopulation = 0;
    String resultChecksum = null;
    String schemaHash = null;
    String runtimePlanHash = null;
    for (int measurement = 0;
         measurement < config.benchmarkMeasurements(); measurement++) {
      Measurement value = execute(config, initialState, true);
      setupNanos = Math.addExact(setupNanos, value.setupNanos);
      tickNanos = Math.addExact(tickNanos, value.tickNanos);
      minimumTickNanos = Math.min(minimumTickNanos, value.tickNanos);
      maximumTickNanos = Math.max(maximumTickNanos, value.tickNanos);
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
      populationGrowthCount = Math.max(
          populationGrowthCount, value.populationGrowthCount);
      maximumPopulation = Math.max(
          maximumPopulation, value.maximumPopulation);
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
        + "\"artifact\":\"grassing-simulation-benchmark-v1\","
        + "\"profile\":\"" + selector + "\","
        + "\"inputChecksum\":\"" + initialState.checksum() + "\","
        + "\"resultChecksum\":\"" + resultChecksum + "\","
        + "\"schemaHash\":\"" + schemaHash + "\","
        + "\"runtimePlanHash\":\"" + runtimePlanHash + "\","
        + "\"ticks\":" + config.ticks() + ","
        + "\"initialPopulation\":" + config.initialPopulation() + ","
        + "\"maximumPopulation\":" + maximumPopulation + ","
        + "\"warmup\":" + config.benchmarkWarmup() + ","
        + "\"measurements\":" + config.benchmarkMeasurements() + ","
        + "\"setupNanos\":" + setupNanos + ","
        + "\"tickNanos\":" + tickNanos + ","
        + "\"minimumTickNanos\":" + minimumTickNanos + ","
        + "\"maximumTickNanos\":" + maximumTickNanos + ","
        + "\"allocatedBytes\":" + allocatedBytes + ","
        + "\"youngGcCount\":" + youngGcCount + ","
        + "\"youngGcPauseMillis\":" + youngGcMillis + ","
        + "\"fullGcCount\":" + fullGcCount + ","
        + "\"fullGcPauseMillis\":" + fullGcMillis + ","
        + "\"exactIndexHighWaterBytes\":" + exactIndexHighWater + ","
        + "\"updateScratchHighWaterBytes\":" + updateScratchHighWater + ","
        + "\"operationScratchHighWaterBytes\":"
        + operationScratchHighWater + ","
        + "\"populationGrowthCount\":" + populationGrowthCount + ","
        + "\"claimAllowed\":false}");
  }

  private static Measurement execute(
      SimulationConfig config, SimulationScenario initialState,
      boolean measured) {
    long setupStart = System.nanoTime();
    SimulationRuntime runtime =
        new SimulationRuntimeFactory().create(initialState);
    SimulationEngine engine = new SimulationEngine(runtime);
    long setupNanos = System.nanoTime() - setupStart;
    try {
      long beforeAllocation = measured
          ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
      JvmMetrics.GcSnapshot beforeGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long tickStart = System.nanoTime();
      SimulationResult result = engine.run();
      long tickNanos = System.nanoTime() - tickStart;
      JvmMetrics.GcSnapshot afterGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long allocated = measured
          ? Math.subtractExact(JvmMetrics.currentThreadAllocatedBytes(),
              beforeAllocation) : 0L;
      SimulationValidator.validate(config, runtime, result);
      SimulationRuntime.RuntimeEvidence evidence =
          runtime.runtimeEvidence();
      return new Measurement(
          setupNanos, tickNanos, allocated,
          measured ? delta(afterGc.youngCount, beforeGc.youngCount) : 0L,
          measured ? delta(afterGc.youngMillis, beforeGc.youngMillis) : 0L,
          measured ? delta(afterGc.fullCount, beforeGc.fullCount) : 0L,
          measured ? delta(afterGc.fullMillis, beforeGc.fullMillis) : 0L,
          evidence.exactIndexHighWaterBytes,
          evidence.updateScratchHighWaterBytes,
          evidence.operationScratchHighWaterBytes,
          evidence.populationGrowthCount,
          result.maximumPopulation(), result.resultChecksum(),
          result.diagnostics().schemaHash(),
          result.diagnostics().runtimePlanHash());
    } finally {
      runtime.close();
    }
  }

  private static long delta(long after, long before) {
    return Math.max(0L, after - before);
  }

  private static final class Measurement {
    final long setupNanos;
    final long tickNanos;
    final long allocatedBytes;
    final long youngGcCount;
    final long youngGcMillis;
    final long fullGcCount;
    final long fullGcMillis;
    final long exactIndexHighWater;
    final long updateScratchHighWater;
    final long operationScratchHighWater;
    final long populationGrowthCount;
    final int maximumPopulation;
    final String resultChecksum;
    final String schemaHash;
    final String runtimePlanHash;

    Measurement(long setupNanos, long tickNanos, long allocatedBytes,
                long youngGcCount, long youngGcMillis,
                long fullGcCount, long fullGcMillis,
                long exactIndexHighWater,
                long updateScratchHighWater,
                long operationScratchHighWater,
                long populationGrowthCount,
                int maximumPopulation, String resultChecksum,
                String schemaHash, String runtimePlanHash) {
      this.setupNanos = setupNanos;
      this.tickNanos = tickNanos;
      this.allocatedBytes = allocatedBytes;
      this.youngGcCount = youngGcCount;
      this.youngGcMillis = youngGcMillis;
      this.fullGcCount = fullGcCount;
      this.fullGcMillis = fullGcMillis;
      this.exactIndexHighWater = exactIndexHighWater;
      this.updateScratchHighWater = updateScratchHighWater;
      this.operationScratchHighWater = operationScratchHighWater;
      this.populationGrowthCount = populationGrowthCount;
      this.maximumPopulation = maximumPopulation;
      this.resultChecksum = resultChecksum;
      this.schemaHash = schemaHash;
      this.runtimePlanHash = runtimePlanHash;
    }
  }
}
