package example.i2stale.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class Gamma {

    @SomaKey
    RenamedCode code;

    @SomaIndex
    int bucket;

    @SomaField
    double score;
}
