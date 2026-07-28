package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class CandidateKey {
  @SomaField public WorkKey workKey;
  @SomaField public GroupId groupId;
}
