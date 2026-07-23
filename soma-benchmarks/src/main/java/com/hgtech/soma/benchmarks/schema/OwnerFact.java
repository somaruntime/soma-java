package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaChild;
import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaKey;
import com.hgtech.soma.annotation.SomaTable;
import com.hgtech.soma.annotation.SomaUnique;
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
