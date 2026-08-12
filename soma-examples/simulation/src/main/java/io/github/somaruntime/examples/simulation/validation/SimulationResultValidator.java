package io.github.somaruntime.examples.simulation.validation;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.engine.api.SimulationStatistics;

/** Small result-boundary validator; it reports rather than repairs state. */
public final class SimulationResultValidator {
    private SimulationResultValidator() {}

    public static void validate(SimulationConfig config, SimulationStatistics statistics) {
        if (statistics.population() < 0
                || statistics.grazingCount() + statistics.searchingCount()
                    != statistics.population()) {
            throw new IllegalStateException("behavior counts do not match population");
        }
        long expectedPopulation = Math.addExact(
                config.initialGrassers(),
                Math.subtractExact(statistics.births(), statistics.deaths()));
        if (expectedPopulation != statistics.population()) {
            throw new IllegalStateException("birth/death population balance is invalid");
        }
        if (statistics.occupiedCells() < 0L
                || statistics.occupiedCells() > config.worldCells()) {
            throw new IllegalStateException("occupied cell count is invalid");
        }
        boolean energyPresent = statistics.meanEnergy().isPresent()
                && statistics.minEnergy().isPresent()
                && statistics.maxEnergy().isPresent();
        if ((statistics.population() == 0 && energyPresent)
                || (statistics.population() != 0
                    && (!energyPresent
                        || !(statistics.minEnergy().getAsDouble() > 0.0)
                        || statistics.maxEnergy().getAsDouble()
                            < statistics.minEnergy().getAsDouble()))) {
            throw new IllegalStateException("energy summary is invalid");
        }
        if (statistics.minGrass() < -1.0e-6
                || statistics.maxGrass() > config.grassCapacity() + 1.0e-6) {
            throw new IllegalStateException("grass summary is outside capacity");
        }
    }
}
