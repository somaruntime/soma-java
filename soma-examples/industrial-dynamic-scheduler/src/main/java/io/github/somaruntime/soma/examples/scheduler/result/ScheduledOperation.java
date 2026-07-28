package io.github.somaruntime.soma.examples.scheduler.result;

/**
 * 脱离 SOMA runtime 生命周期的单项调度结果。
 *
 * <p>该类型是应用结果模型，不是列式 schema record。</p>
 */
public final class ScheduledOperation {
  public final long jobId;
  public final long operationId;
  public final long machineId;
  public final long resourceId;
  public final long setupFamilyId;
  public final long setupStartMinute;
  public final long setupMinutes;
  public final long transportMinutes;
  public final long startMinute;
  public final long processingMinutes;
  public final long endMinute;
  public final long dueMinute;
  public final int priority;

  public ScheduledOperation(
      long jobId, long operationId, long machineId, long resourceId,
      long setupFamilyId, long setupStartMinute, long setupMinutes,
      long transportMinutes, long startMinute, long processingMinutes,
      long endMinute, long dueMinute, int priority) {
    if (jobId <= 0L || operationId <= 0L || machineId <= 0L
        || resourceId <= 0L || setupFamilyId <= 0L) {
      throw new IllegalArgumentException(
          "scheduled operation identity must be positive");
    }
    if (setupStartMinute < 0L || setupMinutes < 0L
        || transportMinutes < 0L || processingMinutes <= 0L
        || dueMinute < 0L || priority <= 0) {
      throw new IllegalArgumentException(
          "scheduled operation timing and priority are invalid");
    }
    if (startMinute != Math.addExact(
        setupStartMinute, setupMinutes)
        || endMinute != Math.addExact(
            startMinute, processingMinutes)) {
      throw new IllegalArgumentException(
          "scheduled operation interval arithmetic is invalid");
    }
    this.jobId = jobId;
    this.operationId = operationId;
    this.machineId = machineId;
    this.resourceId = resourceId;
    this.setupFamilyId = setupFamilyId;
    this.setupStartMinute = setupStartMinute;
    this.setupMinutes = setupMinutes;
    this.transportMinutes = transportMinutes;
    this.startMinute = startMinute;
    this.processingMinutes = processingMinutes;
    this.endMinute = endMinute;
    this.dueMinute = dueMinute;
    this.priority = priority;
  }
}
