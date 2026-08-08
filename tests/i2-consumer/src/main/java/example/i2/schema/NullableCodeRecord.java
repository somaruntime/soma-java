package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 0L)
final class NullableCodeRecord {

    @SomaKey
    NullableCode key;

    @SomaIndex
    NullableCode code;

    @SomaField
    long value;
}
