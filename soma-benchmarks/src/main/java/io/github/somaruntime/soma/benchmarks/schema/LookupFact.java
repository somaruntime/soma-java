package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "lookup_facts", defaultCapacity = 65536)
public final class LookupFact {
  @SomaKey public LookupKey lookupKey;
  @SomaField public long primaryMetric;
  @SomaField public long secondaryMetric;
}
