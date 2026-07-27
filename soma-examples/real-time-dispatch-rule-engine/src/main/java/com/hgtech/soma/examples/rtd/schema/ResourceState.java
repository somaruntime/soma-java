package com.hgtech.soma.examples.rtd.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "rtd_resource_states", defaultCapacity = 256)
public final class ResourceState {
  @SomaKey public ResourceId resourceId;
  @SomaField public int capability;
  @SomaField public long availableMinute;
  @SomaField public boolean enabled;
  @SomaField public long version;
}
