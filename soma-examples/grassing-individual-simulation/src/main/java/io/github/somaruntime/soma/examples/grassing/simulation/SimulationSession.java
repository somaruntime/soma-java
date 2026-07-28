package io.github.somaruntime.soma.examples.grassing.simulation;

import io.github.somaruntime.soma.examples.grassing.result.SimulationResult;

/** 一个 one-shot、同步且显式拥有 live runtime 的仿真会话。 */
public interface SimulationSession extends AutoCloseable {
  boolean hasNextTick();

  void step();

  SimulationResult currentResult();

  SimulationResult finish();

  @Override
  void close();
}
