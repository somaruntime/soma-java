package io.github.somaruntime.soma.examples.rtd.feed;

/** Detached、不可变的待派工业务输入。 */
public final class WorkItem {
  private final long id;
  private final int capability;
  private final long releaseMinute;
  private final long dueMinute;
  private final int priority;
  private final long processingMinutes;

  public WorkItem(
      long id,
      int capability,
      long releaseMinute,
      long dueMinute,
      int priority,
      long processingMinutes) {
    if (id <= 0L) throw new IllegalArgumentException("work id must be positive");
    if (capability < 0) {
      throw new IllegalArgumentException("capability must be non-negative");
    }
    if (releaseMinute < 0L) {
      throw new IllegalArgumentException(
          "release minute must be non-negative");
    }
    if (dueMinute < releaseMinute) {
      throw new IllegalArgumentException(
          "due minute must not precede release");
    }
    if (priority < 0) {
      throw new IllegalArgumentException("priority must be non-negative");
    }
    if (processingMinutes <= 0L) {
      throw new IllegalArgumentException(
          "processing minutes must be positive");
    }
    this.id = id;
    this.capability = capability;
    this.releaseMinute = releaseMinute;
    this.dueMinute = dueMinute;
    this.priority = priority;
    this.processingMinutes = processingMinutes;
  }

  public long id() { return id; }
  public int capability() { return capability; }
  public long releaseMinute() { return releaseMinute; }
  public long dueMinute() { return dueMinute; }
  public int priority() { return priority; }
  public long processingMinutes() { return processingMinutes; }
}
