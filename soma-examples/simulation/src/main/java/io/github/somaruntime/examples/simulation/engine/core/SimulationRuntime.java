package io.github.somaruntime.examples.simulation.engine.core;

import io.github.somaruntime.examples.simulation.configuration.SimulationConfig;
import io.github.somaruntime.examples.simulation.runtime.GrassCellStateTable;
import io.github.somaruntime.examples.simulation.runtime.GrasserStateTable;
import io.github.somaruntime.examples.simulation.runtime.SomaGroup;
import java.util.Random;

/** Package-local owner of the two authoritative SOMA Tables and model control state. */
final class SimulationRuntime {
    final SimulationConfig config;
    final SomaGroup group;
    final GrassCellStateTable grassCells;
    final GrasserStateTable grassers;
    final Random random;
    long tick;
    long births;
    long deaths;
    int nextGrasserId;
    boolean failed;

    SimulationRuntime(SimulationConfig config, SomaGroup group) {
        this.config = config;
        this.group = group;
        this.grassCells = group.grassCellStateTable();
        this.grassers = group.grasserStateTable();
        this.random = new Random(config.randomSeed());
        this.nextGrasserId = config.initialGrassers();
    }

    int cellId(float x, float y) {
        return ((int) y) * config.worldWidth() + (int) x;
    }

    int nextGrasserId() {
        if (nextGrasserId == Integer.MAX_VALUE) {
            throw new IllegalStateException("grasser identity exceeds int structural domain");
        }
        return nextGrasserId++;
    }
}
