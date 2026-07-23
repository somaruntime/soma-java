package com.hgtech.soma.examples.grassing.simulation;

import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;

/** 仿真应用的 canonical execution facade。 */
public interface Simulator {
  SimulationResult run(SimulationScenario scenario);

  SimulationSession prepare(SimulationScenario scenario);
}
