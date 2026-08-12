package io.github.somaruntime.examples.simulation.engine.api;

/** Detached cumulative process timing; headless benchmark excludes presentation work. */
public final class SimulationProcessTimings {
    private final long grassGrowthNanos;
    private final long metabolismNanos;
    private final long reproductionNanos;
    private final long grazingNanos;
    private final long searchingNanos;

    public SimulationProcessTimings(
            long grassGrowthNanos,
            long metabolismNanos,
            long reproductionNanos,
            long grazingNanos,
            long searchingNanos) {
        this.grassGrowthNanos = requireNonNegative(grassGrowthNanos);
        this.metabolismNanos = requireNonNegative(metabolismNanos);
        this.reproductionNanos = requireNonNegative(reproductionNanos);
        this.grazingNanos = requireNonNegative(grazingNanos);
        this.searchingNanos = requireNonNegative(searchingNanos);
    }

    public long grassGrowthNanos() { return grassGrowthNanos; }
    public long metabolismNanos() { return metabolismNanos; }
    public long reproductionNanos() { return reproductionNanos; }
    public long grazingNanos() { return grazingNanos; }
    public long searchingNanos() { return searchingNanos; }

    public long totalNanos() {
        return Math.addExact(
                Math.addExact(grassGrowthNanos, metabolismNanos),
                Math.addExact(
                        reproductionNanos,
                        Math.addExact(grazingNanos, searchingNanos)));
    }

    private static long requireNonNegative(long value) {
        if (value < 0L) throw new IllegalArgumentException("negative process timing");
        return value;
    }
}
