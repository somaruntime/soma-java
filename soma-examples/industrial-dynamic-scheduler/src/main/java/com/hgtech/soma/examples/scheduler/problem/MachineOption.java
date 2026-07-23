package com.hgtech.soma.examples.scheduler.problem;

/** 一个 operation 在指定机器上的处理时长。 */
public final class MachineOption {
  public final long machineId;
  public final long processingMinutes;

  public MachineOption(long machineId, long processingMinutes) {
    this.machineId = machineId;
    this.processingMinutes = processingMinutes;
  }
}
