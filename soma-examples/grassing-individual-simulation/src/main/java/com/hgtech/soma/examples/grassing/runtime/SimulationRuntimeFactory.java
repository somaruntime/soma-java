package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.schema.generated.SchemaMetadata;
import com.hgtech.soma.examples.grassing.schema.generated.TraceSampleTable;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.SomaGroup;
import com.hgtech.soma.runtime.SomaGroupPlan;

/** 创建并完整投影一个 live SOMA runtime aggregate。 */
public final class SimulationRuntimeFactory {
  public SimulationRuntime create(SimulationScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    RuntimePlan plan = plan(scenario.config(), scenario.population());
    SomaGroup group = SomaGroup.create(groupPlan(plan));
    SimulationRuntime runtime = null;
    try {
      GrasserStateTable grassers =
          GrasserStateTable.attach(group, "grasser-states");
      TraceSampleTable traces =
          TraceSampleTable.attach(group, "trace-samples");
      runtime = new SimulationRuntime(
          scenario.config(), scenario.checksum(),
          group, grassers, traces, scenario.grassCopy());
      RuntimeProjector.project(scenario, runtime);
      return runtime;
    } catch (RuntimeException failure) {
      cleanup(runtime, group, failure);
      throw failure;
    } catch (Error failure) {
      cleanup(runtime, group, failure);
      throw failure;
    }
  }

  private static void cleanup(
      SimulationRuntime runtime,
      SomaGroup group,
      Throwable failure) {
    try {
      if (runtime != null) {
        runtime.close();
      } else {
        group.release();
      }
    } catch (Throwable cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }

  private static SomaGroupPlan groupPlan(RuntimePlan plan) {
    return SomaGroupPlan.builder("grassing-simulation-session")
        .member("grasser-states", SchemaMetadata.metadata(),
            GrasserStateTable.metadata(), plan)
        .member("trace-samples", SchemaMetadata.metadata(),
            TraceSampleTable.metadata(), plan)
        .build();
  }

  private static RuntimePlan plan(
      SimulationConfig config, int initialPopulation) {
    RuntimePlan base = GrasserStateTable.defaultRuntimePlan();
    int traceCapacity = Math.max(
        2, config.ticks() / config.traceInterval() + 2);
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(), 2L * 1024L * 1024L * 1024L));
    setCapacity(
        builder, "grasser_states", Math.max(1, initialPopulation));
    setCapacity(builder, "trace_samples", traceCapacity);
    return builder.build();
  }

  private static void setCapacity(
      RuntimePlan.Builder builder,
      String table, int capacity) {
    builder.table(table).initialCapacity(Math.max(1, capacity));
  }
}
