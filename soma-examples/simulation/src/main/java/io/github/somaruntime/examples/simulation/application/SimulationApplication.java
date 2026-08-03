package io.github.somaruntime.examples.simulation.application;

import io.github.somaruntime.examples.simulation.soma.Soma;
import io.github.somaruntime.soma.SomaConfiguration;

/** Runnable qualification entry point for the simulation scenario. */
public final class SimulationApplication {
    private SimulationApplication() {
    }

    public static void main(String[] args) {
        Soma.configure(SomaConfiguration.builder().build());
        SimulationScenario scenario = new SimulationScenario();
        scenario.loadInitialState();
        if (scenario.pendingAt(5L) != 2L) {
            throw new AssertionError("event index lookup");
        }
        scenario.stepTo(5L);
        if (scenario.stateOf(10) != 7L || scenario.pendingEvents() != 1L) {
            throw new AssertionError("deterministic event step");
        }
        scenario.cancel(3L);
        if (scenario.pendingAt(10L) != 0L) {
            throw new AssertionError("cancelled event filter");
        }
        System.out.println("simulation qualification PASS: state=" + scenario.stateOf(10));
    }
}
