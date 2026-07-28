package io.github.somaruntime.soma.examples.grassing.simulation;

import io.github.somaruntime.soma.examples.grassing.runtime.SimulationRuntime;
import io.github.somaruntime.soma.examples.grassing.runtime.SimulationRuntimeFactory;
import io.github.somaruntime.soma.examples.grassing.runtime.SimulationRuntimeTestAccess;
import io.github.somaruntime.soma.examples.grassing.scenario.SimulationScenario;

/** Session fail-stop 对 ordinary runtime failure 与 unexpected Error 的代表性证据。 */
public final class SimulationSessionLifecycleVerification {
  private SimulationSessionLifecycleVerification() {
  }

  public static void verify(SimulationScenario scenario) {
    verifyRuntimeFailure(scenario);
    verifyUnexpectedError(scenario);
  }

  private static void verifyRuntimeFailure(SimulationScenario scenario) {
    SimulationRuntime runtime =
        new SimulationRuntimeFactory().create(scenario);
    SimulationSessionLifecycle lifecycle =
        new SimulationSessionLifecycle(runtime);
    IllegalStateException primary =
        new IllegalStateException("injected runtime failure");
    try {
      throw lifecycle.fail(primary);
    } catch (IllegalStateException actual) {
      require(actual == primary, "runtime primary failure was replaced");
    }
    requireReleased(runtime, "runtime failure");
    lifecycle.close();
  }

  private static void verifyUnexpectedError(SimulationScenario scenario) {
    SimulationRuntime runtime =
        new SimulationRuntimeFactory().create(scenario);
    SimulationSessionLifecycle lifecycle =
        new SimulationSessionLifecycle(runtime);
    AssertionError primary = new AssertionError("injected unexpected error");
    try {
      throw lifecycle.fail(primary);
    } catch (AssertionError actual) {
      require(actual == primary, "Error primary failure was replaced");
    }
    requireReleased(runtime, "unexpected Error");
    lifecycle.close();
  }

  private static void requireReleased(
      final SimulationRuntime runtime, String label) {
    boolean rejected = false;
    try {
      SimulationRuntimeTestAccess.population(runtime);
    } catch (RuntimeException expected) {
      rejected = true;
    }
    require(rejected, label + " did not release runtime");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
