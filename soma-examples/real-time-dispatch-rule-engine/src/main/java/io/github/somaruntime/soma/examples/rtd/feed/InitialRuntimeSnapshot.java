package io.github.somaruntime.soma.examples.rtd.feed;

import java.util.Arrays;
import java.util.Comparator;

/** 与 live SOMA state 分离的初始 runtime snapshot。 */
public final class InitialRuntimeSnapshot {
  private static final Comparator<WorkItem> WORK_BY_ID =
      new Comparator<WorkItem>() {
        @Override
        public int compare(WorkItem left, WorkItem right) {
          return Long.compare(left.id(), right.id());
        }
      };
  private static final Comparator<ResourceSnapshot> RESOURCE_BY_ID =
      new Comparator<ResourceSnapshot>() {
        @Override
        public int compare(
            ResourceSnapshot left, ResourceSnapshot right) {
          return Long.compare(left.id(), right.id());
        }
      };

  private final WorkItem[] work;
  private final ResourceSnapshot[] resources;

  public InitialRuntimeSnapshot(
      WorkItem[] work, ResourceSnapshot[] resources) {
    if (work == null) throw new NullPointerException("work");
    if (resources == null) throw new NullPointerException("resources");
    this.work = Arrays.copyOf(work, work.length);
    this.resources = Arrays.copyOf(resources, resources.length);
    for (WorkItem item : this.work) {
      if (item == null) throw new NullPointerException("work item");
    }
    for (ResourceSnapshot resource : this.resources) {
      if (resource == null) throw new NullPointerException("resource");
    }
    Arrays.sort(this.work, WORK_BY_ID);
    Arrays.sort(this.resources, RESOURCE_BY_ID);
    requireUniqueWork();
    requireUniqueResources();
  }

  public int workCount() { return work.length; }
  public int resourceCount() { return resources.length; }
  public WorkItem workAt(int index) { return work[index]; }
  public ResourceSnapshot resourceAt(int index) { return resources[index]; }
  public WorkItem[] work() { return Arrays.copyOf(work, work.length); }
  public ResourceSnapshot[] resources() {
    return Arrays.copyOf(resources, resources.length);
  }

  private void requireUniqueWork() {
    for (int index = 1; index < work.length; index++) {
      if (work[index - 1].id() == work[index].id()) {
        throw new IllegalArgumentException(
            "duplicate initial work id: " + work[index].id());
      }
    }
  }

  private void requireUniqueResources() {
    for (int index = 1; index < resources.length; index++) {
      if (resources[index - 1].id() == resources[index].id()) {
        throw new IllegalArgumentException(
            "duplicate resource id: " + resources[index].id());
      }
    }
  }
}
