package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.schema.generated.TraceSampleTable;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** 创建并完整投影一个 live SOMA runtime aggregate。 */
public final class SimulationRuntimeFactory {
  public SimulationRuntime create(SimulationScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    RuntimePlan plan = plan(scenario.config(), scenario.population());
    GrasserStateTable grassers = null;
    TraceSampleTable traces = null;
    SimulationRuntime runtime = null;
    try {
      grassers = GrasserStateTable.create(plan);
      traces = TraceSampleTable.create(plan);
      runtime = new SimulationRuntime(
          scenario.config(), scenario.checksum(),
          grassers, traces, scenario.grassCopy());
      RuntimeProjector.project(scenario, runtime);
      RuntimeProjectionVerifier.verify(scenario, runtime);
      return runtime;
    } catch (RuntimeException failure) {
      cleanup(runtime, traces, grassers, failure);
      throw failure;
    } catch (Error failure) {
      cleanup(runtime, traces, grassers, failure);
      throw failure;
    }
  }

  private static void cleanup(
      SimulationRuntime runtime,
      TraceSampleTable traces,
      GrasserStateTable grassers,
      Throwable failure) {
    try {
      if (runtime != null) {
        runtime.close();
        return;
      }
      if (traces != null) traces.release();
    } catch (Throwable cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
    if (runtime == null && grassers != null) {
      try {
        grassers.release();
      } catch (Throwable cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
    }
  }

  private static RuntimePlan plan(
      SimulationConfig config, int initialPopulation) {
    RuntimePlan base = GrasserStateTable.defaultRuntimePlan();
    int traceCapacity = Math.max(
        2, config.ticks() / config.traceInterval() + 2);
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(), 2L * 1024L * 1024L * 1024L));
    replaceCapacity(
        builder, base, "grasser_states", Math.max(1, initialPopulation));
    replaceCapacity(builder, base, "trace_samples", traceCapacity);
    return builder.build();
  }

  private static void replaceCapacity(
      RuntimePlan.Builder builder, RuntimePlan base,
      String table, int capacity) {
    TablePlan replacement = base.requireTable(table).toBuilder()
        .initialCapacity(Math.max(1, capacity)).build();
    builder.replaceTable(replacement);
  }
}
