package com.hgtech.soma.examples.scheduler.state;

import com.hgtech.soma.annotation.SomaField;
import com.hgtech.soma.annotation.SomaValue;

@SomaValue
public class OperationKey {
  @SomaField public JobId jobId;
  @SomaField public OperationId operationId;
}
