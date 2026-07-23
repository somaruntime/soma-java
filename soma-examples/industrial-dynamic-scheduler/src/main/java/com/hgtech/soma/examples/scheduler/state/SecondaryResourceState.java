package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "secondary_resource_states", defaultCapacity = 64)
public final class SecondaryResourceState {
  @SomaKey public ResourceId resourceId;
  @SomaField public int capacity;
  @SomaField public long nextAvailableMinute;
  @SomaField public long version;
}
