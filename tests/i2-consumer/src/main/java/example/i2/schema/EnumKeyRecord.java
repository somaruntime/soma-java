package example.i2.schema;

import example.i2.Status;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 0L)
final class EnumKeyRecord {

    @SomaKey
    Status key;

    @SomaField
    long value;
}
