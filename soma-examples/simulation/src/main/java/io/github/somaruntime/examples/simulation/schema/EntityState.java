package io.github.somaruntime.examples.simulation.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4_096L)
final class EntityState {
    @SomaKey long entityId;
    @SomaField long value;
    @SomaField long processedEvents;
}
