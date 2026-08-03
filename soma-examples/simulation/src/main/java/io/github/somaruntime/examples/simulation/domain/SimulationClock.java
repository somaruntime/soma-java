package io.github.somaruntime.examples.simulation.domain;

/** Deterministic simulation clock owned by the application. */
public final class SimulationClock {
    private long minute;

    public long minute() {
        return minute;
    }

    public void advanceTo(long nextMinute) {
        if (nextMinute < minute) {
            throw new IllegalArgumentException("simulation time cannot move backwards");
        }
        minute = nextMinute;
    }
}
