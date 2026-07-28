package io.github.somaruntime.soma.examples.grassing.scenario;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;

/** 从生效配置创建 detached Scenario。 */
public interface SimulationScenarioFactory {
  SimulationScenario create(SimulationConfig config);
}
