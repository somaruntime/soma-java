package io.github.somaruntime.examples.dispatch.soma.schema;

import io.github.somaruntime.examples.dispatch.domain.DispatchStatus;
import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;

/** High-frequency pending work state for the dispatch loop. */
@SomaTable(defaultCapacity = 4096L)
final class PendingDispatch {
    @SomaKey long requestId;
    @SomaIndex int machineId;
    @SomaIndex int priority;
    @SomaField long releaseMinute;
    @SomaField long processingMinutes;
    @SomaField DispatchStatus status;
}
