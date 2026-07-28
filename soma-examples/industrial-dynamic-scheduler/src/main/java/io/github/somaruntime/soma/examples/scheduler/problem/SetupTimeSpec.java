package io.github.somaruntime.soma.examples.scheduler.problem;

/** 机器与前后 setup family 对应的换型时长。 */
public final class SetupTimeSpec {
  public final long machineId;
  public final long fromFamily;
  public final long toFamily;
  public final long minutes;

  public SetupTimeSpec(long machineId, long fromFamily, long toFamily,
                       long minutes) {
    this.machineId = machineId;
    this.fromFamily = fromFamily;
    this.toFamily = toFamily;
    this.minutes = minutes;
  }
}
