package com.hgtech.soma.examples.rtd.feed;

import java.util.Arrays;
import java.util.Comparator;

/** 一个 cycle 的 detached work-arrival delta。 */
public final class WorkArrivalDelta {
  private static final Comparator<WorkItem> BY_ID =
      new Comparator<WorkItem>() {
        @Override
        public int compare(WorkItem left, WorkItem right) {
          return Long.compare(left.id(), right.id());
        }
      };

  private final WorkItem[] arrivals;

  public WorkArrivalDelta(WorkItem[] arrivals) {
    if (arrivals == null) throw new NullPointerException("arrivals");
    this.arrivals = Arrays.copyOf(arrivals, arrivals.length);
    for (WorkItem item : this.arrivals) {
      if (item == null) throw new NullPointerException("arrival");
    }
    Arrays.sort(this.arrivals, BY_ID);
    for (int index = 1; index < this.arrivals.length; index++) {
      if (this.arrivals[index - 1].id() == this.arrivals[index].id()) {
        throw new IllegalArgumentException(
            "duplicate work arrival id: " + this.arrivals[index].id());
      }
    }
  }

  public int size() { return arrivals.length; }

  public WorkItem itemAt(int index) {
    if (index < 0 || index >= arrivals.length) {
      throw new IndexOutOfBoundsException("arrival index");
    }
    return arrivals[index];
  }

  public WorkItem[] items() {
    return Arrays.copyOf(arrivals, arrivals.length);
  }
}
