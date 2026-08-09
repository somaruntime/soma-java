package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 0L)
final class ScaleRecord {

    @SomaKey
    long id;

    @SomaIndex
    long bucket;

    @SomaIndex
    int shard;

    @SomaField
    int value;
}
