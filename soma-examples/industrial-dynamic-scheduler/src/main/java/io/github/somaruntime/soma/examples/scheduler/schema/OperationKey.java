package io.github.somaruntime.soma.examples.scheduler.schema;

import io.github.somaruntime.soma.annotation.SomaField;
import io.github.somaruntime.soma.annotation.SomaValue;

@SomaValue
public class OperationKey {
  @SomaField public JobId jobId;
  @SomaField public OperationId operationId;
}
