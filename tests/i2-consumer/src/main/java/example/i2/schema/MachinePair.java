package example.i2.schema;

import example.i2.Status;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaValue;

@SomaValue
final class MachinePair {

    @SomaField
    MachineId fromMachine;

    @SomaField
    MachineId toMachine;

    @SomaField
    String label;

    @SomaField
    Status status;
}
