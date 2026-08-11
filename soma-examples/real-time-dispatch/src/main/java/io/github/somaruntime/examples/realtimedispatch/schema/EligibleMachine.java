package io.github.somaruntime.examples.realtimedispatch.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 16_384)
final class EligibleMachine {
    @SomaKey long eligibilityId;
    @SomaIndex long jobId;
    @SomaIndex long machineId;
    @SomaField long processingMinutes;
    @SomaField int priority;
}
