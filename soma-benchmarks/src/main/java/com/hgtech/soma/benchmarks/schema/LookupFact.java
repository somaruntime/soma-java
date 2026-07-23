package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "lookup_facts", defaultCapacity = 65536)
public final class LookupFact {
  @SomaKey public LookupKey lookupKey;
  @SomaField public long primaryMetric;
  @SomaField public long secondaryMetric;
}
