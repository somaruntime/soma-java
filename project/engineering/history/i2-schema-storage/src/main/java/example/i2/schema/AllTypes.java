package example.i2.schema;

import example.i2.Payload;
import example.i2.Status;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;
import java.util.List;

@SomaTable(defaultCapacity = 4L)
final class AllTypes {

    @SomaKey
    MachinePair key;

    @SomaIndex
    String name;

    @SomaIndex
    MachineId machineId;

    @SomaField
    boolean enabled;

    @SomaField
    byte byteValue;

    @SomaField
    short shortValue;

    @SomaField
    char charValue;

    @SomaField
    int intValue;

    @SomaField
    long longValue;

    @SomaField
    float ratio;

    @SomaField
    double weight;

    @SomaField
    Status status;

    @SomaField
    Payload payload;

    @SomaField
    byte[] bytes;

    @SomaField
    List<String> tags;
}
