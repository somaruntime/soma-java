package com.hgtech.soma.examples.grassing.simulation;

import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationEngine;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntimeFactory;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;

/** 使用 SOMA columnar runtime 执行场景的 production implementation。 */
public final class SomaSimulator implements Simulator {
  @Override
  public SimulationResult run(SimulationScenario scenario) {
    SimulationSession session = prepare(scenario);
    try {
      return session.finish();
    } finally {
      session.close();
    }
  }

  @Override
  public SimulationSession prepare(SimulationScenario scenario) {
    if (scenario == null) throw new NullPointerException("scenario");
    SimulationRuntime runtime = new SimulationRuntimeFactory().create(scenario);
    boolean complete = false;
    try {
      SimulationSession session = new SomaSimulationSession(
          runtime, new SimulationEngine(runtime));
      complete = true;
      return session;
    } finally {
      if (!complete) runtime.close();
    }
  }

  private static final class SomaSimulationSession
      implements SimulationSession {
    private final SimulationEngine engine;
    private final SimulationSessionLifecycle lifecycle;

    SomaSimulationSession(
        SimulationRuntime runtime, SimulationEngine engine) {
      lifecycle = new SimulationSessionLifecycle(runtime);
      this.engine = engine;
    }

    @Override
    public boolean hasNextTick() {
      lifecycle.requireActive();
      return engine.hasNextTick();
    }

    @Override
    public void step() {
      lifecycle.requireActive();
      if (!engine.hasNextTick()) {
        throw new IllegalStateException("simulation has no remaining tick");
      }
      try {
        engine.step();
        lifecycle.markRunning();
      } catch (RuntimeException failure) {
        throw lifecycle.fail(failure);
      } catch (Error failure) {
        throw lifecycle.fail(failure);
      }
    }

    @Override
    public SimulationResult currentResult() {
      lifecycle.requireActive();
      try {
        return engine.currentResult();
      } catch (RuntimeException failure) {
        throw lifecycle.fail(failure);
      } catch (Error failure) {
        throw lifecycle.fail(failure);
      }
    }

    @Override
    public SimulationResult finish() {
      lifecycle.requireActive();
      try {
        SimulationResult completed = engine.run();
        lifecycle.finish();
        return completed;
      } catch (RuntimeException failure) {
        throw lifecycle.fail(failure);
      } catch (Error failure) {
        throw lifecycle.fail(failure);
      }
    }

    @Override
    public void close() {
      lifecycle.close();
    }
  }
}
