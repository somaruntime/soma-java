package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaIndex;
import io.github.somaruntime.soma.annotation.SomaKey;
import io.github.somaruntime.soma.annotation.SomaTable;

@SomaTable(name = "group_candidates", defaultCapacity = 8192)
@SomaIndex(name = "by_group", fields = {"candidateKey.groupId.value"})
@SomaIndex(name = "by_work", fields = {
    "candidateKey.workKey.namespaceId.value",
    "candidateKey.workKey.itemId.value"})
public final class GroupCandidate {
  @SomaKey public CandidateKey candidateKey;
  @SomaField public CategoryId categoryId;
  @SomaField public long metric0;
  @SomaField public long metric1;
  @SomaField public long metric2;
  @SomaField public long metric3;
  @SomaField public long metric4;
  @SomaField public long metric5;
  @SomaField public long metric6;
  @SomaField public long metric7;
  @SomaField public long metric8;
  @SomaField public boolean selected;
}
