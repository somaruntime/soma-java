package io.github.somaruntime.examples.simulation.configuration;

import io.github.somaruntime.soma.SomaCompression;

/** Immutable, fully validated model, runtime and visualization configuration. */
public final class SimulationConfig {
    private final int worldWidth;
    private final int worldHeight;
    private final int initialGrassers;
    private final float initialGrass;
    private final float initialEnergy;
    private final float satiationEnergy;
    private final float grassCapacity;
    private final float grassGrowthRate;
    private final float metabolism;
    private final float reproductionProbability;
    private final float grazingConsumption;
    private final float minimumGrassAfterGrazing;
    private final float resumeGrazingAt;
    private final float walkSpeed;
    private final float turnStdDevRadians;
    private final long randomSeed;
    private final long maxTicks;
    private final long statisticsEveryTicks;
    private final long memoryBudgetBytes;
    private final SomaCompression compression;
    private final boolean visualizationEnabled;
    private final long renderEveryTicks;
    private final int cellSize;
    private final long frameDelayMillis;

    public SimulationConfig(
            int worldWidth,
            int worldHeight,
            int initialGrassers,
            float initialGrass,
            float initialEnergy,
            float satiationEnergy,
            float grassCapacity,
            float grassGrowthRate,
            float metabolism,
            float reproductionProbability,
            float grazingConsumption,
            float minimumGrassAfterGrazing,
            float resumeGrazingAt,
            float walkSpeed,
            float turnStdDevRadians,
            long randomSeed,
            long maxTicks,
            long statisticsEveryTicks,
            long memoryBudgetBytes,
            SomaCompression compression,
            boolean visualizationEnabled,
            long renderEveryTicks,
            int cellSize,
            long frameDelayMillis) {
        if (worldWidth <= 0 || worldHeight <= 0 || initialGrassers < 0) {
            throw new IllegalArgumentException("world dimensions must be positive and population non-negative");
        }
        Math.multiplyExact(worldWidth, worldHeight);
        requireFinite(initialGrass, "initialGrass");
        requireFinite(initialEnergy, "initialEnergy");
        requireFinite(satiationEnergy, "satiationEnergy");
        requireFinite(grassCapacity, "grassCapacity");
        requireFinite(grassGrowthRate, "grassGrowthRate");
        requireFinite(metabolism, "metabolism");
        requireFinite(reproductionProbability, "reproductionProbability");
        requireFinite(grazingConsumption, "grazingConsumption");
        requireFinite(minimumGrassAfterGrazing, "minimumGrassAfterGrazing");
        requireFinite(resumeGrazingAt, "resumeGrazingAt");
        requireFinite(walkSpeed, "walkSpeed");
        requireFinite(turnStdDevRadians, "turnStdDevRadians");
        if (grassCapacity <= 0.0f
                || initialGrass < 0.0f || initialGrass > grassCapacity
                || initialEnergy <= 0.0f || initialEnergy > satiationEnergy
                || satiationEnergy <= 0.0f
                || grassGrowthRate < 0.0f || metabolism < 0.0f
                || reproductionProbability < 0.0f || reproductionProbability > 1.0f
                || minimumGrassAfterGrazing < 0.0f
                || minimumGrassAfterGrazing >= resumeGrazingAt
                || resumeGrazingAt > grassCapacity
                || grazingConsumption <= 0.0f
                || grazingConsumption >= grassCapacity - minimumGrassAfterGrazing
                || walkSpeed < 0.0f || turnStdDevRadians < 0.0f) {
            throw new IllegalArgumentException("model parameter range is invalid");
        }
        if (maxTicks < 0L || statisticsEveryTicks <= 0L
                || memoryBudgetBytes <= 0L || renderEveryTicks <= 0L
                || cellSize <= 0 || frameDelayMillis < 0L) {
            throw new IllegalArgumentException("run or visualization parameter range is invalid");
        }
        if (compression == null) {
            throw new NullPointerException("compression");
        }
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.initialGrassers = initialGrassers;
        this.initialGrass = initialGrass;
        this.initialEnergy = initialEnergy;
        this.satiationEnergy = satiationEnergy;
        this.grassCapacity = grassCapacity;
        this.grassGrowthRate = grassGrowthRate;
        this.metabolism = metabolism;
        this.reproductionProbability = reproductionProbability;
        this.grazingConsumption = grazingConsumption;
        this.minimumGrassAfterGrazing = minimumGrassAfterGrazing;
        this.resumeGrazingAt = resumeGrazingAt;
        this.walkSpeed = walkSpeed;
        this.turnStdDevRadians = turnStdDevRadians;
        this.randomSeed = randomSeed;
        this.maxTicks = maxTicks;
        this.statisticsEveryTicks = statisticsEveryTicks;
        this.memoryBudgetBytes = memoryBudgetBytes;
        this.compression = compression;
        this.visualizationEnabled = visualizationEnabled;
        this.renderEveryTicks = renderEveryTicks;
        this.cellSize = cellSize;
        this.frameDelayMillis = frameDelayMillis;
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    public int worldWidth() { return worldWidth; }
    public int worldHeight() { return worldHeight; }
    public int worldCells() { return Math.multiplyExact(worldWidth, worldHeight); }
    public int initialGrassers() { return initialGrassers; }
    public float initialGrass() { return initialGrass; }
    public float initialEnergy() { return initialEnergy; }
    public float satiationEnergy() { return satiationEnergy; }
    public float grassCapacity() { return grassCapacity; }
    public float grassGrowthRate() { return grassGrowthRate; }
    public float metabolism() { return metabolism; }
    public float reproductionProbability() { return reproductionProbability; }
    public float grazingConsumption() { return grazingConsumption; }
    public float minimumGrassAfterGrazing() { return minimumGrassAfterGrazing; }
    public float resumeGrazingAt() { return resumeGrazingAt; }
    public float walkSpeed() { return walkSpeed; }
    public float turnStdDevRadians() { return turnStdDevRadians; }
    public long randomSeed() { return randomSeed; }
    public long maxTicks() { return maxTicks; }
    public long statisticsEveryTicks() { return statisticsEveryTicks; }
    public long memoryBudgetBytes() { return memoryBudgetBytes; }
    public SomaCompression compression() { return compression; }
    public boolean visualizationEnabled() { return visualizationEnabled; }
    public long renderEveryTicks() { return renderEveryTicks; }
    public int cellSize() { return cellSize; }
    public long frameDelayMillis() { return frameDelayMillis; }

    public SimulationConfig withVisualizationEnabled(boolean enabled) {
        return new SimulationConfig(
                worldWidth, worldHeight, initialGrassers, initialGrass, initialEnergy,
                satiationEnergy, grassCapacity, grassGrowthRate, metabolism,
                reproductionProbability, grazingConsumption, minimumGrassAfterGrazing,
                resumeGrazingAt, walkSpeed, turnStdDevRadians, randomSeed, maxTicks,
                statisticsEveryTicks, memoryBudgetBytes, compression, enabled,
                renderEveryTicks, cellSize, frameDelayMillis);
    }

    public SimulationConfig withMaxTicks(long ticks) {
        return new SimulationConfig(
                worldWidth, worldHeight, initialGrassers, initialGrass, initialEnergy,
                satiationEnergy, grassCapacity, grassGrowthRate, metabolism,
                reproductionProbability, grazingConsumption, minimumGrassAfterGrazing,
                resumeGrazingAt, walkSpeed, turnStdDevRadians, randomSeed, ticks,
                statisticsEveryTicks, memoryBudgetBytes, compression, visualizationEnabled,
                renderEveryTicks, cellSize, frameDelayMillis);
    }
}
