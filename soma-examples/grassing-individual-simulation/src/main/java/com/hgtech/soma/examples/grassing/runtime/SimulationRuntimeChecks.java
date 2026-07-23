package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.schema.BehaviourMode;
import com.hgtech.soma.examples.grassing.schema.GrasserId;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateBatch;
import com.hgtech.soma.runtime.IndexSnapshot;

/** reference application 边界上的 key、Index 与 lifecycle 负路径。 */
public final class SimulationRuntimeChecks {
  private SimulationRuntimeChecks() {
  }

  public static void verify(
      SimulationConfig config, SimulationScenario scenario) {
    SimulationRuntime runtime =
        new SimulationRuntimeFactory().create(scenario);
    try {
      SimulationEngine engine = new SimulationEngine(runtime);
      IndexSnapshot current = runtime.grassers.indexSnapshot();
      IndexSnapshot wrongSource = runtime.traces.indexSnapshot();
      expectFailure(new Action() {
        @Override
        public void run() {
          runtime.grassers.requireCurrent(wrongSource);
        }
      }, "wrong-source snapshot");

      long firstId = scenario.individuals().get(0).id();
      require(runtime.grassers.containsKey(new GrasserId(firstId)),
          "primary key lookup failed");
      int index = runtime.grassers.requireIndex(firstId);
      double energy = runtime.grassers.fetchAt(index).energy;
      runtime.grassers.mutateAt(index).setEnergy(energy).commit();

      GrasserStateBatch duplicate = new GrasserStateBatch(1);
      duplicate.addValues(new GrasserId(firstId), 0, 0, 1.0,
          BehaviourMode.GRASSING, 0);
      int beforeDuplicate = runtime.grassers.size();
      expectFailure(new Action() {
        @Override
        public void run() {
          runtime.grassers.addBatch(duplicate);
        }
      }, "duplicate append");
      require(runtime.grassers.size() == beforeDuplicate,
          "failed append changed table size");

      runtime.grassers.delete(new GrasserId(firstId));
      expectFailure(new Action() {
        @Override
        public void run() {
          runtime.grassers.requireCurrent(current);
        }
      }, "stale snapshot");
      require(runtime.grassers.size() == beforeDuplicate - 1,
          "swap-remove did not remove exactly one individual");
      runtime.traces.clear();
      require(runtime.traces.size() == 0, "trace clear failed");
      require(engine.currentTick() == 0L,
          "runtime boundary check unexpectedly advanced simulation");
    } finally {
      runtime.close();
    }
    expectFailure(new Action() {
      @Override
      public void run() {
        runtime.population();
      }
    }, "released runtime access");
  }

  private static void expectFailure(Action action, String label) {
    boolean rejected = false;
    try {
      action.run();
    } catch (RuntimeException expected) {
      rejected = true;
    }
    require(rejected, "runtime boundary accepted " + label);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private interface Action {
    void run();
  }
}
