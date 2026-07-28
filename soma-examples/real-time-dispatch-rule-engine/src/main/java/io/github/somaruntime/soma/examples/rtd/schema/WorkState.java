package io.github.somaruntime.soma.examples.rtd.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "rtd_work_states", defaultCapacity = 4096)
@SomaIndex(name = "by_status", fields = {"status"})
public final class WorkState {
  @SomaKey public WorkId workId;
  @SomaField public int capability;
  @SomaField public long releaseMinute;
  @SomaField public long dueMinute;
  @SomaField public int priority;
  @SomaField public long processingMinutes;
  @SomaField public WorkStatus status;
  @SomaField public long version;
}
