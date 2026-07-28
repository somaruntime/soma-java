package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "transport_times", defaultCapacity = 4096)
public final class TransportTime {
  @SomaKey public MachinePairKey machinePair;
  @SomaField public long transportMinutes;
}
