package io.github.somaruntime.benchmarks.simulation.kernel.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Non-production narrow benchmark state; not part of the Grassing Example. */
@SomaTable(defaultCapacity = 4_096)
final class EntityState {
    @SomaKey long entityId;
    @SomaField long value;
    @SomaField long processedEvents;
}
