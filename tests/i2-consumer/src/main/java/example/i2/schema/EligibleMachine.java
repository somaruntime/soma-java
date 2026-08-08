package example.i2.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaTable;

@SomaTable
final class EligibleMachine {

    @SomaIndex
    JobId jobId;

    @SomaIndex
    MachineId machineId;

    @SomaField
    long processingMinutes;
}
