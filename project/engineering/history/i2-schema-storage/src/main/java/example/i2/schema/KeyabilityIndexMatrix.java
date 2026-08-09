package example.i2.schema;

import example.i2.Status;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 0L)
final class KeyabilityIndexMatrix {

    @SomaIndex
    boolean booleanValue;

    @SomaIndex
    byte byteValue;

    @SomaIndex
    short shortValue;

    @SomaIndex
    char charValue;

    @SomaIndex
    int intValue;

    @SomaIndex
    long longValue;

    @SomaIndex
    String stringValue;

    @SomaIndex
    Status status;

    @SomaIndex
    MachineId machineId;

    @SomaField
    FloatingValue floatingValue;
}
