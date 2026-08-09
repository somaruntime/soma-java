package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class LogEntry {

    @SomaField
    long minute;

    @SomaField
    String message;
}
