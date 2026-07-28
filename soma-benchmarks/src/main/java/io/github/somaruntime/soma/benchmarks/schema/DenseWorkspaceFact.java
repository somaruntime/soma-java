package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "dense_workspace_facts", defaultCapacity = 16384)
public final class DenseWorkspaceFact {
  @SomaField public GroupId groupId;
  @SomaField public ItemId itemId;
  @SomaField public int ordinal;
  @SomaField public long version;
  @SomaField public long sortMetric;
  @SomaField public long projectedMetric;
  @SomaField public int projectedCount;
  @SomaField public long totalMetric;
}
