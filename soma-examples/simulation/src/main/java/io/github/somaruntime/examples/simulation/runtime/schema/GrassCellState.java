package io.github.somaruntime.examples.simulation.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Authoritative grass quantity for one stable world cell. */
@SomaTable(defaultCapacity = 16_384)
final class GrassCellState {
    @SomaKey int cellId;
    @SomaField float grass;
}
