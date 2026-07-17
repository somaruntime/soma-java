package com.hgtech.soma.examples.fjsp.schema;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class OperationKey {
  @SomaField JobId jobId;
  @SomaField OperationId operationId;
}
