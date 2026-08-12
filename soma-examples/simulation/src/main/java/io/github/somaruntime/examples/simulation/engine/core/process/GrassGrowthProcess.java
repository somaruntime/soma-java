package io.github.somaruntime.examples.simulation.engine.core.process;

import io.github.somaruntime.examples.simulation.runtime.GrassCellStateTable;

/** Table-local logistic growth over all cells. */
public final class GrassGrowthProcess {
    private final GrassCellStateTable grassCells;
    private final float rate;
    private final float capacity;

    public GrassGrowthProcess(GrassCellStateTable grassCells, float rate, float capacity) {
        this.grassCells = grassCells;
        this.rate = rate;
        this.capacity = capacity;
    }

    public void execute() {
        grassCells.selectAll().update(editor -> {
            float grass = editor.grass();
            float next = grass + rate * grass * (capacity - grass) / capacity;
            editor.grass(Math.max(0.0f, Math.min(capacity, next)));
        });
    }
}
