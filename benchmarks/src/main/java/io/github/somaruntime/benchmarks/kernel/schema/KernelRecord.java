package io.github.somaruntime.benchmarks.kernel.schema;

import io.github.somaruntime.benchmarks.kernel.domain.KernelPayload;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 16_384)
final class KernelRecord {
    @SomaKey long recordId;
    @SomaIndex RouteKey route;
    @SomaIndex String tenant;
    @SomaIndex KernelStatus status;
    @SomaField byte code;
    @SomaField short laneNumber;
    @SomaField char category;
    @SomaField int quantity;
    @SomaField long amount;
    @SomaField float ratio;
    @SomaField double score;
    @SomaField String label;
    @SomaField KernelPayload payload;
}
