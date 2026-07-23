package com.hgtech.soma.examples.grassing.validation;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.model.SimulationInitialState;
import com.hgtech.soma.examples.grassing.runtime.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;

/** 只向 evidence 层暴露逐 tick 断言，不暴露 AoS 内部模型。 */
public final class ReferenceSimulationBridge {
  private final ReferenceSimulation reference;

  public ReferenceSimulationBridge(
      SimulationConfig config, SimulationInitialState initialState) {
    reference = new ReferenceSimulation(config, initialState);
  }

  public void stepAndAssert(
      SimulationRuntime runtime, SimulationResult runtimeResult) {
    reference.step();
    SimulationValidator.assertEquivalent(runtime, reference, runtimeResult);
  }
}
