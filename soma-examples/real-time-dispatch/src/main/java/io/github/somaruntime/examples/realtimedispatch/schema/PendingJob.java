package io.github.somaruntime.examples.realtimedispatch.schema;

import io.github.somaruntime.soma.SomaField;
import io.github.somaruntime.soma.SomaIndex;
import io.github.somaruntime.soma.SomaKey;
import io.github.somaruntime.soma.SomaTable;
import io.github.somaruntime.examples.realtimedispatch.domain.DispatchPayload;

@SomaTable(defaultCapacity = 4_096)
final class PendingJob {
    @SomaKey long jobId;
    @SomaIndex PendingStatus status;
    @SomaIndex String queue;
    @SomaField long releaseMinute;
    @SomaField long deadlineMinute;
    @SomaField DispatchPayload payload;
}
