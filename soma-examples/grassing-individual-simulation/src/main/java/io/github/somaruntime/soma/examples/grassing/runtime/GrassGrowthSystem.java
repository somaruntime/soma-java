package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;

/** 执行 grass grid 的 logistic growth。 */
final class GrassGrowthSystem {
  private final SimulationRuntime runtime;
  private final SimulationConfig config;

  GrassGrowthSystem(SimulationRuntime runtime) {
    this.runtime = runtime;
    config = runtime.config;
  }

  void execute() {
    double capacity = config.grassCarryingCapacity();
    double rate = config.grassGrowthRate();
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      double grass = runtime.grass[cell];
      double grown = grass + rate * grass * (1.0 - grass / capacity);
      runtime.grass[cell] = Math.min(capacity, Math.max(0.0, grown));
    }
  }
}
