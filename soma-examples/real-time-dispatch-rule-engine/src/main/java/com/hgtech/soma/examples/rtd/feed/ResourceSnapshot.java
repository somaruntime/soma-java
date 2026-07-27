package com.hgtech.soma.examples.rtd.feed;

/** Detached、不可变的资源初始事实。 */
public final class ResourceSnapshot {
  private final long id;
  private final int capability;
  private final long availableMinute;
  private final boolean enabled;

  public ResourceSnapshot(
      long id, int capability, long availableMinute, boolean enabled) {
    if (id <= 0L) {
      throw new IllegalArgumentException("resource id must be positive");
    }
    if (capability < 0) {
      throw new IllegalArgumentException("capability must be non-negative");
    }
    if (availableMinute < 0L) {
      throw new IllegalArgumentException(
          "available minute must be non-negative");
    }
    this.id = id;
    this.capability = capability;
    this.availableMinute = availableMinute;
    this.enabled = enabled;
  }

  public long id() { return id; }
  public int capability() { return capability; }
  public long availableMinute() { return availableMinute; }
  public boolean enabled() { return enabled; }
}
