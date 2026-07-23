package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class WorkKey {
  @SomaField public NamespaceId namespaceId;
  @SomaField public ItemId itemId;
}
