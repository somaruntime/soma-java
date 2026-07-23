package com.hgtech.soma.benchmarks.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class CandidateKey {
  @SomaField public WorkKey workKey;
  @SomaField public GroupId groupId;
}
