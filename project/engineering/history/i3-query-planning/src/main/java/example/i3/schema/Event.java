package example.i3.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 32)
final class Event {
    @SomaKey long id;
    @SomaIndex String kind;
    @SomaField boolean enabled;
    @SomaField byte code;
    @SomaField short shortValue;
    @SomaField char letter;
    @SomaField int priority;
    @SomaField long amount;
    @SomaField float ratio;
    @SomaField double score;
    @SomaField String label;
    @SomaField Object payload;
    @SomaField RouteKey route;
    @SomaField Status status;
}
