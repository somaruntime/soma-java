package com.hgtech.soma.examples.scheduler.problem;

/** 一个 job 的不可变输入定义。 */
public final class JobSpec {
  public final long id;
  public final long releaseMinute;
  public final long materialReadyMinute;
  public final long dueMinute;
  public final int priority;
  public final int operationCount;

  public JobSpec(long id, long releaseMinute, long materialReadyMinute,
                 long dueMinute, int priority, int operationCount) {
    this.id = id;
    this.releaseMinute = releaseMinute;
    this.materialReadyMinute = materialReadyMinute;
    this.dueMinute = dueMinute;
    this.priority = priority;
    this.operationCount = operationCount;
  }
}
