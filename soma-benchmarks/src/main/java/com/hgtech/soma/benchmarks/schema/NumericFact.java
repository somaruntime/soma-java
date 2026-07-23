package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaTable;

@SomaTable(name = "numeric_facts", defaultCapacity = 4096)
public final class NumericFact {
  @SomaField public int factIndex;
  @SomaField public EntityKind entityKind;
  @SomaField public long entityId;
  @SomaField public VariableKind variableKind;
  @SomaField public double value;
  @SomaField public double derivative;
  @SomaField public double scale;
}
