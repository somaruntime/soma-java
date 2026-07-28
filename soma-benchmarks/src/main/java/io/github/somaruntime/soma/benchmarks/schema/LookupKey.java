package io.github.somaruntime.soma.benchmarks.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class LookupKey {
  @SomaField public LookupId left;
  @SomaField public LookupId right;
}
