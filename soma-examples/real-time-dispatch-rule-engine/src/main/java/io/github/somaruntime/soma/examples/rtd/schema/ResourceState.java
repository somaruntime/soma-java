package io.github.somaruntime.soma.examples.rtd.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "rtd_resource_states", defaultCapacity = 256)
public final class ResourceState {
  @SomaKey public ResourceId resourceId;
  @SomaField public int capability;
  @SomaField public long availableMinute;
  @SomaField public boolean enabled;
  @SomaField public long version;
}
