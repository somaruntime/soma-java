package io.github.somaruntime.soma.examples.rtd.result;

/** DataFlow 候选推导后立即复制出的 detached dispatch command。 */
public final class DispatchCommand {
  private final long workId;
  private final long resourceId;
  private final int capability;
  private final long issueMinute;
  private final long startMinute;
  private final long completionMinute;
  private final int priority;
  private final long expectedWorkVersion;
  private final long expectedResourceVersion;

  public DispatchCommand(
      long workId,
      long resourceId,
      int capability,
      long issueMinute,
      long startMinute,
      long completionMinute,
      int priority,
      long expectedWorkVersion,
      long expectedResourceVersion) {
    if (workId <= 0L || resourceId <= 0L) {
      throw new IllegalArgumentException(
          "command identities must be positive");
    }
    if (capability < 0) {
      throw new IllegalArgumentException(
          "command capability must be non-negative");
    }
    if (issueMinute < 0L || startMinute < issueMinute
        || completionMinute <= startMinute) {
      throw new IllegalArgumentException(
          "invalid command time interval");
    }
    if (priority < 0
        || expectedWorkVersion < 0L
        || expectedResourceVersion < 0L) {
      throw new IllegalArgumentException(
          "invalid command priority or version");
    }
    this.workId = workId;
    this.resourceId = resourceId;
    this.capability = capability;
    this.issueMinute = issueMinute;
    this.startMinute = startMinute;
    this.completionMinute = completionMinute;
    this.priority = priority;
    this.expectedWorkVersion = expectedWorkVersion;
    this.expectedResourceVersion = expectedResourceVersion;
  }

  public long workId() { return workId; }
  public long resourceId() { return resourceId; }
  public int capability() { return capability; }
  public long issueMinute() { return issueMinute; }
  public long startMinute() { return startMinute; }
  public long completionMinute() { return completionMinute; }
  public int priority() { return priority; }
  public long expectedWorkVersion() { return expectedWorkVersion; }
  public long expectedResourceVersion() {
    return expectedResourceVersion;
  }
}
