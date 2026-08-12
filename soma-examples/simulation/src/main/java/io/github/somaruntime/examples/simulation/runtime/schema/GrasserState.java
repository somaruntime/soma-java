package io.github.somaruntime.examples.simulation.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Authoritative runtime state of one grazing/searching individual. */
@SomaTable(defaultCapacity = 4_096)
final class GrasserState {
    @SomaKey int grasserId;
    @SomaIndex int cellId;
    @SomaField boolean searching;
    @SomaField float x;
    @SomaField float y;
    @SomaField float energy;
    @SomaField float direction;
}
