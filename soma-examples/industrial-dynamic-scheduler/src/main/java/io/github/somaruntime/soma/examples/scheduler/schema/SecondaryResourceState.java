package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "secondary_resource_states", defaultCapacity = 64)
public final class SecondaryResourceState {
  @SomaKey public ResourceId resourceId;
  @SomaField public int capacity;
  @SomaField public long version;
}
