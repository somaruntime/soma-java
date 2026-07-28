package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "job_definitions", defaultCapacity = 1024)
public final class JobDefinition {
  @SomaKey public JobId jobId;
  @SomaField public long releaseMinute;
  @SomaField public long materialReadyMinute;
  @SomaField public long dueMinute;
  @SomaField public int priority;
}
