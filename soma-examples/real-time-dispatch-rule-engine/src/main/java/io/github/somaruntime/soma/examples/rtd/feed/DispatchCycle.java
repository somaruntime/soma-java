package io.github.somaruntime.soma.examples.rtd.feed;

/** 一次规则 Invocation 的 detached 时间与增量输入。 */
public final class DispatchCycle {
  private final long currentMinute;
  private final WorkArrivalDelta arrivals;

  public DispatchCycle(
      long currentMinute, WorkArrivalDelta arrivals) {
    if (currentMinute < 0L) {
      throw new IllegalArgumentException(
          "current minute must be non-negative");
    }
    if (arrivals == null) throw new NullPointerException("arrivals");
    for (WorkItem item : arrivals.items()) {
      if (item.releaseMinute() > currentMinute) {
        throw new IllegalArgumentException(
            "arrival release exceeds cycle time");
      }
    }
    this.currentMinute = currentMinute;
    this.arrivals = arrivals;
  }

  public long currentMinute() { return currentMinute; }
  public WorkArrivalDelta arrivals() { return arrivals; }
}
