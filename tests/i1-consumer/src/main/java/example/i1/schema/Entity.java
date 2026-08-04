package example.i1.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4L)
final class Entity {

    @SomaKey
    long id;

    @SomaField
    long value;
}
