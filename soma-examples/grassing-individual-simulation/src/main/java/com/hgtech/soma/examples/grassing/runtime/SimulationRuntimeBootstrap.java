package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.state.BehaviourMode;
import com.hgtech.soma.examples.grassing.state.GrasserId;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateBatch;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.state.generated.TraceSampleTable;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** detached Scenario 到 authoritative runtime 的唯一装载边界。 */
public final class SimulationRuntimeBootstrap {
  private SimulationRuntimeBootstrap() {
  }

  public static SimulationRuntime load(SimulationScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    SimulationConfig config = scenario.config();
    RuntimePlan plan = plan(config, scenario);
    GrasserStateTable grassers = GrasserStateTable.create(plan);
    TraceSampleTable traces = TraceSampleTable.create(plan);
    SimulationRuntime runtime = new SimulationRuntime(
        config, scenario.checksum(),
        grassers, traces, scenario.grassCopy());
    boolean complete = false;
    try {
      GrasserStateBatch batch =
          new GrasserStateBatch(scenario.population());
      long maximumId = 0L;
      for (IndividualSeed individual : scenario.individuals()) {
        batch.addValues(new GrasserId(individual.id()),
            individual.x(), individual.y(), individual.energy(),
            mode(individual.mode()), individual.movementDirection());
        maximumId = Math.max(maximumId, individual.id());
      }
      grassers.replaceAll(batch);
      runtime.initializeNextId(Math.addExact(maximumId, 1L));
      verifyProjection(scenario, runtime);
      complete = true;
      return runtime;
    } finally {
      if (!complete) runtime.close();
    }
  }

  private static RuntimePlan plan(
      SimulationConfig config, SimulationScenario scenario) {
    RuntimePlan base = GrasserStateTable.defaultRuntimePlan();
    int populationCapacity = Math.max(1, scenario.population());
    int traceCapacity = Math.max(
        2, config.ticks() / config.traceInterval() + 2);
    RuntimePlan.Builder builder = base.toBuilder()
        .maximumAggregateStorageBytes(Math.max(
            base.maximumAggregateStorageBytes(), 2L * 1024L * 1024L * 1024L));
    replaceCapacity(
        builder, base, "grasser_states", populationCapacity);
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

  private static BehaviourMode mode(int ordinal) {
    if (ordinal == IndividualSeed.MODE_GRASSING) {
      return BehaviourMode.GRASSING;
    }
    if (ordinal == IndividualSeed.MODE_SEARCHING) {
      return BehaviourMode.SEARCHING;
    }
    throw new IllegalArgumentException("unknown input behaviour mode");
  }

  private static void verifyProjection(
      SimulationScenario scenario, SimulationRuntime runtime) {
    require(runtime.grassers.size() == scenario.population(),
        "population projection");
    require(runtime.traces.size() == 0, "trace table must start empty");
    DoubleColumnView energy = runtime.grassers.energyColumn();
    IntColumnView x = runtime.grassers.xColumn();
    IntColumnView y = runtime.grassers.yColumn();
    try {
      for (IndividualSeed individual : scenario.individuals()) {
        int index = runtime.grassers.requireIndex(individual.id());
        require(Double.doubleToLongBits(energy.getDouble(index))
                == Double.doubleToLongBits(individual.energy())
                && x.getInt(index) == individual.x()
                && y.getInt(index) == individual.y(),
            "individual value projection");
      }
    } finally {
      y.close();
      x.close();
      energy.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
