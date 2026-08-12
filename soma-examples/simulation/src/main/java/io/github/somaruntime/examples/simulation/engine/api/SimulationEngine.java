package io.github.somaruntime.examples.simulation.engine.api;

/** Synchronous engine boundary: exactly one complete model tick per step. */
public interface SimulationEngine {
    void step();
    long tick();
    SimulationStatistics statistics();
    SimulationSnapshot snapshot();
    SimulationProcessTimings processTimings();
}
