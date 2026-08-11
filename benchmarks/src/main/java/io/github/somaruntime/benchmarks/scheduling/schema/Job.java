package io.github.somaruntime.benchmarks.scheduling.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Stable benchmark-only scheduling fixture. */
@SomaTable(defaultCapacity = 4_096)
final class Job {
    @SomaKey long jobId;
    @SomaIndex JobStatus status;
    @SomaField long releaseMinute;
    @SomaField long dueMinute;
}
