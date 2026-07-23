package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.IntColumnView;

/** 投影完成时的结构 invariant 验证。 */
final class RuntimeProjectionVerifier {
  private RuntimeProjectionVerifier() {
  }

  static void verify(
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
