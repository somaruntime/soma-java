package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

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
