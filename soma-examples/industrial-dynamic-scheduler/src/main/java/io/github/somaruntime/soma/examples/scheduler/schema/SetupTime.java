package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "setup_times", defaultCapacity = 4096)
public final class SetupTime {
  @SomaKey public SetupTimeKey setupKey;
  @SomaField public long setupMinutes;
}
