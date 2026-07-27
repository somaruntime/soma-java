package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.schema.GrasserState;
import com.hgtech.soma.examples.grassing.schema.TraceSample;
import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.MaterializationBudget;

import java.util.Arrays;
import java.util.List;

/** 只供 test/evidence 使用的 live runtime 观察边界。 */
public final class SimulationRuntimeTestAccess {
  private SimulationRuntimeTestAccess() {
  }

  public static int population(SimulationRuntime runtime) {
    return runtime.grassers.size();
  }

  public static int traceCount(SimulationRuntime runtime) {
    return runtime.traces.size();
  }

  public static int keyCount(SimulationRuntime runtime) {
    final int[] count = new int[1];
    runtime.grassers.keys().forEach(key ->
        count[0] = Math.addExact(count[0], 1));
    return count[0];
  }

  public static void verifyProjection(
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

  public static double[] grassCopy(SimulationRuntime runtime) {
    runtime.grassers.size();
    return Arrays.copyOf(runtime.grass, runtime.grass.length);
  }

  public static List<GrasserState> materializeIndividuals(
      SimulationRuntime runtime) {
    long rows = Math.max(1L, runtime.grassers.size());
    MaterializationBudget budget = MaterializationBudget.builder()
        .maximumRows(rows)
        .maximumLeafValues(Math.multiplyExact(rows, 8L))
        .maximumTableInstances(1L)
        .maximumOwnershipDepth(1)
        .maximumEstimatedAllocationBytes(Math.max(
            1024L * 1024L, Math.multiplyExact(rows, 128L)))
        .build();
    return runtime.grassers.fetchAll(budget);
  }

  public static List<TraceSample> materializeTraces(
      SimulationRuntime runtime) {
    long rows = Math.max(1L, runtime.traces.size());
    MaterializationBudget budget = MaterializationBudget.builder()
        .maximumRows(rows)
        .maximumLeafValues(Math.multiplyExact(rows, 10L))
        .maximumTableInstances(1L)
        .maximumOwnershipDepth(1)
        .maximumEstimatedAllocationBytes(Math.max(
            1024L * 1024L, Math.multiplyExact(rows, 128L)))
        .build();
    return runtime.traces.fetchAll(budget);
  }

  public static double snapshotEnergySum(SimulationRuntime runtime) {
    IndexSnapshot snapshot = runtime.grassers.indexSnapshot();
    runtime.grassers.requireCurrent(snapshot);
    DoubleColumnView energy = runtime.grassers.energyColumn();
    try {
      double total = 0.0;
      for (int position = 0; position < snapshot.size(); position++) {
        total += energy.getDouble(snapshot.indexAt(position));
      }
      return total;
    } finally {
      energy.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
