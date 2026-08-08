package example.i2stale.schema;

import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class RenamedRelation {

    @SomaIndex
    RenamedCode sourceCode;

    @SomaIndex
    RenamedCode targetCode;
}
