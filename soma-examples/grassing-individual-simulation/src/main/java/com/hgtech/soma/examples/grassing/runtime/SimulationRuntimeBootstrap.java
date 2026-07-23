package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.model.SimulationInitialState;
import com.hgtech.soma.examples.grassing.model.SimulationInitialState.IndividualInput;
import com.hgtech.soma.examples.grassing.state.BehaviourMode;
import com.hgtech.soma.examples.grassing.state.GrasserId;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateBatch;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.state.generated.TraceSampleTable;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.RuntimePlan;
import com.hgtech.soma.runtime.TablePlan;

/** detached initial state 到 authoritative runtime 的唯一装载边界。 */
public final class SimulationRuntimeBootstrap {
  private SimulationRuntimeBootstrap() {
  }

  public static SimulationRuntime load(
      SimulationConfig config, SimulationInitialState initialState) {
    if (config == null) throw new NullPointerException("config");
    if (initialState == null) throw new NullPointerException("initialState");
    validateInput(config, initialState);
    RuntimePlan plan = plan(config, initialState);
    GrasserStateTable grassers = GrasserStateTable.create(plan);
    TraceSampleTable traces = TraceSampleTable.create(plan);
    SimulationRuntime runtime = new SimulationRuntime(
        config, grassers, traces, initialState.grassCopy());
    boolean complete = false;
    try {
      GrasserStateBatch batch =
          new GrasserStateBatch(initialState.population());
      long maximumId = 0L;
      for (IndividualInput individual : initialState.individuals()) {
        batch.addValues(new GrasserId(individual.id),
            individual.x, individual.y, individual.energy,
            mode(individual.mode), individual.movementDirection);
        maximumId = Math.max(maximumId, individual.id);
      }
      grassers.replaceAll(batch);
      runtime.initializeNextId(Math.addExact(maximumId, 1L));
      verifyProjection(initialState, runtime);
      complete = true;
      return runtime;
    } finally {
      if (!complete) runtime.close();
    }
  }

  private static RuntimePlan plan(
      SimulationConfig config, SimulationInitialState initialState) {
    RuntimePlan base = GrasserStateTable.defaultRuntimePlan();
    int populationCapacity = Math.max(1, initialState.population());
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

  private static void validateInput(
      SimulationConfig config, SimulationInitialState initialState) {
    require(initialState.width() == config.width()
            && initialState.height() == config.height(),
        "initial state dimensions differ from config");
    require(initialState.population() == config.initialPopulation(),
        "initial population differs from config");
    for (double grass : initialState.grassCopy()) {
      require(grass <= config.grassCarryingCapacity(),
          "initial grass exceeds configured carrying capacity");
    }
  }

  private static BehaviourMode mode(int ordinal) {
    if (ordinal == SimulationInitialState.MODE_GRASSING) {
      return BehaviourMode.GRASSING;
    }
    if (ordinal == SimulationInitialState.MODE_SEARCHING) {
      return BehaviourMode.SEARCHING;
    }
    throw new IllegalArgumentException("unknown input behaviour mode");
  }

  private static void verifyProjection(
      SimulationInitialState initialState, SimulationRuntime runtime) {
    require(runtime.grassers.size() == initialState.population(),
        "population projection");
    require(runtime.traces.size() == 0, "trace table must start empty");
    DoubleColumnView energy = runtime.grassers.energyColumn();
    IntColumnView x = runtime.grassers.xColumn();
    IntColumnView y = runtime.grassers.yColumn();
    try {
      for (IndividualInput individual : initialState.individuals()) {
        int index = runtime.grassers.requireIndex(individual.id);
        require(Double.doubleToLongBits(energy.getDouble(index))
                == Double.doubleToLongBits(individual.energy)
                && x.getInt(index) == individual.x
                && y.getInt(index) == individual.y,
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
