package io.github.somaruntime.examples.simulation.engine.core.process;

import io.github.somaruntime.examples.simulation.runtime.GrasserStateTable;

/** Applies energy consumption then removes all dead individuals in one Selection mutation. */
public final class MetabolismProcess {
    private final GrasserStateTable grassers;
    private final float metabolism;

    public MetabolismProcess(GrasserStateTable grassers, float metabolism) {
        this.grassers = grassers;
        this.metabolism = metabolism;
    }

    public long execute() {
        grassers.selectAll().update(editor -> editor.energy(editor.energy() - metabolism));
        return grassers.filter(grassers.energy.le(0.0f)).remove().removed();
    }
}
