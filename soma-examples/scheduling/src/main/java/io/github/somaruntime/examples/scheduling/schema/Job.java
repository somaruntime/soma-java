package io.github.somaruntime.examples.scheduling.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

@SomaTable(defaultCapacity = 4_096)
final class Job {
    @SomaKey long jobId;
    @SomaIndex JobStatus status;
    @SomaField long releaseMinute;
    @SomaField long dueMinute;
}
