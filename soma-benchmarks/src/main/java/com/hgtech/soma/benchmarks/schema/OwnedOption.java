package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "owned_options", defaultCapacity = 8)
public final class OwnedOption {
  @SomaField public GroupId groupId;
  @SomaField public long cost;
}
