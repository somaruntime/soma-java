package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateCursor;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateScan;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateUpdateCursor;

/** 执行 metabolism 并 swap-remove 死亡个体。 */
final class MetabolismSystem {
  private final SimulationRuntime runtime;
  private final SimulationConfig config;

  MetabolismSystem(SimulationRuntime runtime) {
    this.runtime = runtime;
    config = runtime.config;
  }

  int execute() {
    runtime.grassers.update(new GrasserStateScan.Updater() {
      @Override
      public void update(GrasserStateUpdateCursor candidate) {
        candidate.setEnergy(candidate.energy() - config.metabolismCost());
      }
    });
    int before = runtime.grassers.size();
    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return candidate.energy() <= 0.0;
      }
    }).remove();
    return before - runtime.grassers.size();
  }
}
