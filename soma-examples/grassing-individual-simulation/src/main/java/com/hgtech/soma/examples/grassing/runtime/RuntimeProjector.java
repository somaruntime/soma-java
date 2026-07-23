package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.schema.BehaviourMode;
import com.hgtech.soma.examples.grassing.schema.GrasserId;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateBatch;

/** Scenario 到 SOMA tables 的一次性投影。 */
final class RuntimeProjector {
  private RuntimeProjector() {
  }

  static void project(
      SimulationScenario scenario, SimulationRuntime runtime) {
    GrasserStateBatch batch =
        new GrasserStateBatch(scenario.population());
    long maximumId = 0L;
    for (IndividualSeed individual : scenario.individuals()) {
      batch.addValues(new GrasserId(individual.id()),
          individual.x(), individual.y(), individual.energy(),
          mode(individual.mode()), individual.movementDirection());
      maximumId = Math.max(maximumId, individual.id());
    }
    runtime.grassers.replaceAll(batch);
    runtime.initializeNextId(Math.addExact(maximumId, 1L));
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
}
