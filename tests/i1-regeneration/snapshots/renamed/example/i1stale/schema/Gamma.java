package example.i1stale.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class Gamma {

    @SomaKey
    long id;

    @SomaField
    long value;
}
