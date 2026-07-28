package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaChild;
import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;
import io.github.somaruntime.soma.annotation.SomaUnique;
import java.util.List;

@SomaTable(name = "owner_facts", defaultCapacity = 4096)
@SomaUnique(name = "by_namespace_sequence", fields = {
    "workKey.namespaceId.value", "sequence"})
public final class OwnerFact {
  @SomaKey public WorkKey workKey;
  @SomaField public int sequence;
  @SomaField public long releaseValue;
  @SomaField public CategoryId categoryId;
  @SomaChild(initialCapacity = 8)
  public List<OwnedOption> options;
}
