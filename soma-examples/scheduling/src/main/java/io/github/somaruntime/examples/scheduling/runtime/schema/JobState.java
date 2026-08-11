package io.github.somaruntime.examples.scheduling.runtime.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** Runtime progress and result summary of one job. */
@SomaTable(defaultCapacity = 1_024)
final class JobState {
    @SomaKey long jobId;
    @SomaField long completedOperationCount;
    @SomaField long completionTime;
    @SomaField JobStatus status;
}
