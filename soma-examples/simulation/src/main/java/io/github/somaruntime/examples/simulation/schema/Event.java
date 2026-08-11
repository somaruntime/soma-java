package io.github.somaruntime.examples.simulation.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 16_384)
final class Event {
    @SomaKey long eventId;
    @SomaIndex long entityId;
    @SomaField long eventMinute;
    @SomaField int priority;
    @SomaField EventType eventType;
    @SomaField long delta;
}
