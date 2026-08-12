package io.github.somaruntime.examples.simulation.engine.api;

import java.util.OptionalDouble;

/** Detached immutable statistics at one complete tick boundary. */
public final class SimulationStatistics {
    private final long tick;
    private final int population;
    private final long births;
    private final long deaths;
    private final long grazingCount;
    private final long searchingCount;
    private final OptionalDouble meanEnergy;
    private final OptionalDouble minEnergy;
    private final OptionalDouble maxEnergy;
    private final double meanGrass;
    private final double minGrass;
    private final double maxGrass;
    private final long occupiedCells;
    private final long retainedBytes;
    private final long representationBytes;
    private final long fingerprint;

    public SimulationStatistics(
            long tick, int population, long births, long deaths,
            long grazingCount, long searchingCount,
            OptionalDouble meanEnergy, OptionalDouble minEnergy, OptionalDouble maxEnergy,
            double meanGrass, double minGrass, double maxGrass,
            long occupiedCells, long retainedBytes, long representationBytes,
            long fingerprint) {
        this.tick = tick;
        this.population = population;
        this.births = births;
        this.deaths = deaths;
        this.grazingCount = grazingCount;
        this.searchingCount = searchingCount;
        this.meanEnergy = requireEnergy(meanEnergy, "meanEnergy");
        this.minEnergy = requireEnergy(minEnergy, "minEnergy");
        this.maxEnergy = requireEnergy(maxEnergy, "maxEnergy");
        this.meanGrass = meanGrass;
        this.minGrass = minGrass;
        this.maxGrass = maxGrass;
        this.occupiedCells = occupiedCells;
        this.retainedBytes = retainedBytes;
        this.representationBytes = representationBytes;
        this.fingerprint = fingerprint;
    }

    public long tick() { return tick; }
    public int population() { return population; }
    public long births() { return births; }
    public long deaths() { return deaths; }
    public long grazingCount() { return grazingCount; }
    public long searchingCount() { return searchingCount; }
    public OptionalDouble meanEnergy() { return meanEnergy; }
    public OptionalDouble minEnergy() { return minEnergy; }
    public OptionalDouble maxEnergy() { return maxEnergy; }
    public double meanGrass() { return meanGrass; }
    public double minGrass() { return minGrass; }
    public double maxGrass() { return maxGrass; }
    public long occupiedCells() { return occupiedCells; }
    public long retainedBytes() { return retainedBytes; }
    public long representationBytes() { return representationBytes; }
    public long fingerprint() { return fingerprint; }

    private static OptionalDouble requireEnergy(OptionalDouble value, String name) {
        if (value == null) throw new NullPointerException(name);
        return value;
    }
}
