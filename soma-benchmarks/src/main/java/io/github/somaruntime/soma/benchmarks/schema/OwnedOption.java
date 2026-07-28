package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "owned_options", defaultCapacity = 8)
public final class OwnedOption {
  @SomaField public GroupId groupId;
  @SomaField public long cost;
}
