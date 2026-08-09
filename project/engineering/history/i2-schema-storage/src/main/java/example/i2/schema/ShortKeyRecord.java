package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 0L)
final class ShortKeyRecord {

    @SomaKey
    short key;

    @SomaField
    long value;
}
