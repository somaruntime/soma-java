package io.github.somaruntime.examples.scheduling.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 16_384L)
final class ProcessingOption {
    @SomaKey long optionId;
    @SomaIndex long jobId;
    @SomaIndex long machineId;
    @SomaField long processingMinutes;
    @SomaField long setupMinutes;
    @SomaField boolean enabled;
}
