package io.github.somaruntime.examples.simulation.engine.core;

import io.github.somaruntime.examples.simulation.runtime.GrassCellState;
import io.github.somaruntime.examples.simulation.runtime.GrasserState;

/** Streams deterministic initial state directly into SOMA without a shadow object graph. */
final class SimulationInitializer {
    void initialize(SimulationRuntime runtime) {
        runtime.grassCells.reserve(runtime.config.worldCells());
        runtime.grassers.reserve(runtime.config.initialGrassers());
        for (int cellId = 0; cellId < runtime.config.worldCells(); cellId++) {
            runtime.grassCells.add(new GrassCellState(cellId, runtime.config.initialGrass()));
        }
        for (int id = 0; id < runtime.config.initialGrassers(); id++) {
            float x = runtime.random.nextFloat() * runtime.config.worldWidth();
            float y = runtime.random.nextFloat() * runtime.config.worldHeight();
            runtime.grassers.add(new GrasserState(
                    id, runtime.cellId(x, y), false, x, y,
                    runtime.config.initialEnergy(), 0.0f));
        }
    }
}
