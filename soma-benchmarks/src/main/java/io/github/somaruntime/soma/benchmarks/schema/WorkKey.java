package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class WorkKey {
  @SomaField public NamespaceId namespaceId;
  @SomaField public ItemId itemId;
}
