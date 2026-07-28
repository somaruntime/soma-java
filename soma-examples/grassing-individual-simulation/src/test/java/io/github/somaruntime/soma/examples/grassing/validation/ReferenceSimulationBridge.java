package io.github.somaruntime.soma.examples.grassing.validation;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;
import io.github.somaruntime.soma.examples.grassing.result.SimulationResult;
import io.github.somaruntime.soma.examples.grassing.runtime.SimulationRuntime;
import io.github.somaruntime.soma.examples.grassing.scenario.SimulationScenario;

/** 只向 evidence 层暴露逐 tick 断言，不暴露 AoS 内部模型。 */
public final class ReferenceSimulationBridge {
  private final ReferenceSimulation reference;

  public ReferenceSimulationBridge(
      SimulationConfig config, SimulationScenario scenario) {
    reference = new ReferenceSimulation(config, scenario);
  }

  public void stepAndAssert(
      SimulationRuntime runtime, SimulationResult runtimeResult) {
    reference.step();
    SimulationValidator.assertEquivalent(runtime, reference, runtimeResult);
  }
}
