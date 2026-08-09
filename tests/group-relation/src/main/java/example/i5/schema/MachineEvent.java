package example.i5.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 32)
final class MachineEvent {
    @SomaKey long eventId;
    @SomaIndex long machineId;
    @SomaIndex String route;
    @SomaField long duration;
    @SomaField boolean enabled;
}
