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
    private static final int READY = 0;
    private static final int RUNNING = 1;
    private static final int FINISHED = 2;
    private static final int CLOSED = 3;

    private final SimulationRuntime runtime;
    private final SimulationEngine engine;
    private int state = READY;
    private SimulationResult result;

    SomaSimulationSession(
        SimulationRuntime runtime, SimulationEngine engine) {
      this.runtime = runtime;
      this.engine = engine;
    }

    @Override
    public boolean hasNextTick() {
      ensureActive();
      return engine.hasNextTick();
    }

    @Override
    public void step() {
      ensureActive();
      if (!engine.hasNextTick()) {
        throw new IllegalStateException("simulation has no remaining tick");
      }
      try {
        engine.step();
        state = RUNNING;
      } catch (RuntimeException failure) {
        close();
        throw failure;
      }
    }

    @Override
    public SimulationResult currentResult() {
      ensureActive();
      return engine.currentResult();
    }

    @Override
    public SimulationResult finish() {
      ensureActive();
      try {
        result = engine.run();
        state = FINISHED;
        runtime.close();
        return result;
      } catch (RuntimeException failure) {
        close();
        throw failure;
      }
    }

    @Override
    public void close() {
      if (state == CLOSED) return;
      runtime.close();
      state = CLOSED;
    }

    private void ensureActive() {
      if (state == FINISHED) {
        throw new IllegalStateException("simulation session is finished");
      }
      if (state == CLOSED) {
        throw new IllegalStateException("simulation session is closed");
      }
    }
  }
}
