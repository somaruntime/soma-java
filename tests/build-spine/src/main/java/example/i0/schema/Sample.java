package example.i0.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 32)
final class Sample {

    @SomaKey
    long id;

    @SomaField
    String name;
}
