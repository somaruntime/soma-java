package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class LookupKey {
  @SomaField public LookupId left;
  @SomaField public LookupId right;
}
