package com.hgtech.soma.examples.grassing.simulation;

import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;

/** Session lifecycle 与 fail-stop cleanup 的唯一 Owner。 */
final class SimulationSessionLifecycle {
  private static final int READY = 0;
  private static final int RUNNING = 1;
  private static final int FINISHED = 2;
  private static final int CLOSED = 3;

  private final SimulationRuntime runtime;
  private int state = READY;

  SimulationSessionLifecycle(SimulationRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    this.runtime = runtime;
  }

  void requireActive() {
    if (state == FINISHED) {
      throw new IllegalStateException("simulation session is finished");
    }
    if (state == CLOSED) {
      throw new IllegalStateException("simulation session is closed");
    }
  }

  void markRunning() {
    requireActive();
    state = RUNNING;
  }

  void finish() {
    requireActive();
    try {
      runtime.close();
      state = FINISHED;
    } catch (RuntimeException failure) {
      state = CLOSED;
      throw failure;
    } catch (Error failure) {
      state = CLOSED;
      throw failure;
    }
  }

  RuntimeException fail(RuntimeException failure) {
    if (failure == null) throw new NullPointerException("failure");
    closeAfterFailure(failure);
    return failure;
  }

  Error fail(Error failure) {
    if (failure == null) throw new NullPointerException("failure");
    closeAfterFailure(failure);
    return failure;
  }

  void close() {
    if (state == CLOSED) return;
    try {
      runtime.close();
    } finally {
      state = CLOSED;
    }
  }

  private void closeAfterFailure(Throwable primary) {
    if (state == CLOSED) return;
    try {
      runtime.close();
    } catch (Throwable cleanup) {
      if (cleanup != primary) primary.addSuppressed(cleanup);
    } finally {
      state = CLOSED;
    }
  }
}
