package com.hgtech.soma.examples.grassing.evidence;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.config.SimulationConfigLoader;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.scenario.SyntheticSimulationScenarioFactory;
import com.hgtech.soma.examples.grassing.simulation.SimulationSession;
import com.hgtech.soma.examples.grassing.simulation.Simulator;
import com.hgtech.soma.examples.grassing.simulation.SomaSimulator;
import com.hgtech.soma.examples.grassing.validation.SimulationResultAssertions;

/** 单 JVM fork 的 correctness-guarded integrated benchmark。 */
public final class SimulationBenchmark {
  private SimulationBenchmark() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    SimulationConfig config = new SimulationConfigLoader().load(selector);
    BenchmarkOptions options = BenchmarkOptions.load(
        args.length < 2 ? selector : args[1]);
    BenchmarkEnvironment environment =
        new BenchmarkEnvironment(options.forks());
    SimulationScenario scenario =
        new SyntheticSimulationScenarioFactory().create(config);
    for (int warmup = 0; warmup < options.warmup(); warmup++) {
      execute(config, scenario, false);
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
         measurement < options.measurements(); measurement++) {
      Measurement value = execute(config, scenario, true);
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
        + "\"schemaVersion\":\"soma-reference-application-benchmark-v1\","
        + "\"artifactVersion\":\"grassing-simulation-benchmark-v3\","
        + environment.jsonFields() + ","
        + "\"profile\":\"" + selector + "\","
        + "\"inputChecksum\":\"" + scenario.checksum() + "\","
        + "\"resultChecksum\":\"" + resultChecksum + "\","
        + "\"schemaHash\":\"" + schemaHash + "\","
        + "\"runtimePlanHash\":\"" + runtimePlanHash + "\","
        + "\"worldWidth\":" + config.width() + ","
        + "\"worldHeight\":" + config.height() + ","
        + "\"worldCells\":"
        + Math.multiplyExact(config.width(), config.height()) + ","
        + "\"ticks\":" + config.ticks() + ","
        + "\"initialPopulation\":" + config.initialPopulation() + ","
        + "\"maximumPopulation\":" + maximumPopulation + ","
        + "\"warmup\":" + options.warmup() + ","
        + "\"measurements\":" + options.measurements() + ","
        + "\"tickExecutions\":"
        + Math.multiplyExact(config.ticks(), options.measurements()) + ","
        + "\"setupNanos\":" + setupNanos + ","
        + "\"tickNanos\":" + tickNanos + ","
        + "\"minimumTickNanos\":" + minimumTickNanos + ","
        + "\"maximumTickNanos\":" + maximumTickNanos + ","
        + "\"tickNanosPerTick\":"
        + ceilingDivide(tickNanos,
            Math.multiplyExact(config.ticks(), options.measurements())) + ","
        + "\"allocatedBytes\":" + allocatedBytes + ","
        + "\"allocatedBytesPerTick\":"
        + ceilingDivide(allocatedBytes,
            Math.multiplyExact(config.ticks(), options.measurements())) + ","
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

  private static long ceilingDivide(long value, int divisor) {
    if (value < 0L || divisor <= 0) {
      throw new IllegalArgumentException(
          "ceiling division requires non-negative value and positive divisor");
    }
    return value == 0L ? 0L : 1L + (value - 1L) / divisor;
  }

  private static Measurement execute(
      SimulationConfig config, SimulationScenario scenario,
      boolean measured) {
    long setupStart = System.nanoTime();
    Simulator simulator = new SomaSimulator();
    SimulationSession session = simulator.prepare(scenario);
    long setupNanos = System.nanoTime() - setupStart;
    try {
      long beforeAllocation = measured
          ? JvmMetrics.currentThreadAllocatedBytes() : 0L;
      JvmMetrics.GcSnapshot beforeGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long tickStart = System.nanoTime();
      SimulationResult result = session.finish();
      long tickNanos = System.nanoTime() - tickStart;
      JvmMetrics.GcSnapshot afterGc = measured
          ? JvmMetrics.gcSnapshot() : null;
      long allocated = measured
          ? Math.subtractExact(JvmMetrics.currentThreadAllocatedBytes(),
              beforeAllocation) : 0L;
      SimulationResultAssertions.validate(scenario, result);
      return new Measurement(
          setupNanos, tickNanos, allocated,
          measured ? delta(afterGc.youngCount, beforeGc.youngCount) : 0L,
          measured ? delta(afterGc.youngMillis, beforeGc.youngMillis) : 0L,
          measured ? delta(afterGc.fullCount, beforeGc.fullCount) : 0L,
          measured ? delta(afterGc.fullMillis, beforeGc.fullMillis) : 0L,
          result.diagnostics().exactIndexHighWaterBytes(),
          result.diagnostics().updateScratchHighWaterBytes(),
          result.diagnostics().operationScratchHighWaterBytes(),
          result.diagnostics().populationGrowthCount(),
          result.maximumPopulation(), result.resultChecksum(),
          result.diagnostics().schemaHash(),
          result.diagnostics().runtimePlanHash());
    } finally {
      session.close();
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
