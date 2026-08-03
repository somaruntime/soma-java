package io.github.somaruntime.examples.scheduling.soma.schema;

import io.github.somaruntime.examples.scheduling.domain.JobState;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/**
 * SOMA-owned mutable scheduling state.  Cross-table business decisions remain
 * in the application layer; this schema intentionally has one authoritative
 * table until the multi-table relation surface is qualified.
 */
@SomaTable(defaultCapacity = 4096L)
final class Job {
    @SomaKey long jobId;
    @SomaIndex int machineId;
    @SomaIndex int priority;
    @SomaField long releaseMinute;
    @SomaField long processingMinutes;
    @SomaField JobState state;
}
