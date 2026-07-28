package io.github.somaruntime.soma.examples.scheduler.problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一个工序及其可选机器的不可变输入定义。 */
public final class OperationSpec {
  public final long jobId;
  public final long operationId;
  public final int sequence;
  public final long setupFamily;
  public final long resourceId;
  public final int resourceUnits;
  public final List<MachineOption> options;

  public OperationSpec(long jobId, long operationId, int sequence,
                       long setupFamily, long resourceId,
                       int resourceUnits, List<MachineOption> options) {
    if (options == null) throw new NullPointerException("options");
    this.jobId = jobId;
    this.operationId = operationId;
    this.sequence = sequence;
    this.setupFamily = setupFamily;
    this.resourceId = resourceId;
    this.resourceUnits = resourceUnits;
    this.options = Collections.unmodifiableList(
        new ArrayList<MachineOption>(options));
  }
}
