package example.i5.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 16)
final class MachineState {
    @SomaKey long stateId;
    @SomaIndex long machineId;
    @SomaIndex String route;
    @SomaField long availableMinute;
    @SomaField boolean enabled;
}
