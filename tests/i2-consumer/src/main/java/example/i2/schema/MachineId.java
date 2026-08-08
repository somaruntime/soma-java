package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaValue;

@SomaValue
final class MachineId {

    @SomaField
    long value;
}
