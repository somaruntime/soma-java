package io.github.somaruntime.examples.simulation.soma.schema;

import io.github.somaruntime.examples.simulation.domain.EventKind;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Event queue state.  Time is application-encoded as a primitive minute. */
@SomaTable(defaultCapacity = 4096L)
final class SimulationEvent {
    @SomaKey long eventId;
    @SomaIndex long dueMinute;
    @SomaIndex int entityId;
    @SomaField EventKind kind;
    @SomaField long delta;
    @SomaField boolean cancelled;
}
