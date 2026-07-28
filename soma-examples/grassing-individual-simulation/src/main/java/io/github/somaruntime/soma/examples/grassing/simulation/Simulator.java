package io.github.somaruntime.soma.examples.grassing.simulation;

import io.github.somaruntime.soma.examples.grassing.result.SimulationResult;
import io.github.somaruntime.soma.examples.grassing.scenario.SimulationScenario;

/** 仿真应用的 canonical execution facade。 */
public interface Simulator {
  SimulationResult run(SimulationScenario scenario);

  SimulationSession prepare(SimulationScenario scenario);
}
