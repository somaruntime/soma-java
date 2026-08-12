package io.github.somaruntime.examples.simulation.engine.api;

/** Final application-owned execution result. */
public final class SimulationRunResult {
    private final SimulationStatistics statistics;
    private final SimulationProcessTimings processTimings;
    private final long initializationNanos;
    private final long kernelNanos;
    private final boolean valid;

    public SimulationRunResult(
            SimulationStatistics statistics,
            SimulationProcessTimings processTimings,
            long initializationNanos,
            long kernelNanos,
            boolean valid) {
        this.statistics = statistics;
        this.processTimings = processTimings;
        this.initializationNanos = initializationNanos;
        this.kernelNanos = kernelNanos;
        this.valid = valid;
    }

    public SimulationStatistics statistics() { return statistics; }
    public SimulationProcessTimings processTimings() { return processTimings; }
    public long initializationNanos() { return initializationNanos; }
    public long kernelNanos() { return kernelNanos; }
    public boolean valid() { return valid; }

    public double ticksPerSecond() {
        return kernelNanos == 0L ? 0.0
                : statistics.tick() * 1_000_000_000.0 / kernelNanos;
    }
}
