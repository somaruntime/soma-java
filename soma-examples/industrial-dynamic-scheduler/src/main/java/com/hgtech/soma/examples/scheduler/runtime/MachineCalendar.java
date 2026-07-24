package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.MaintenanceInterval;

import java.util.List;

/** 从不可变 machine definition 投影出的连续区间定位结构。 */
final class MachineCalendar {
  private final long[] starts;
  private final long[] ends;

  MachineCalendar(List<MaintenanceInterval> windows) {
    if (windows == null) throw new NullPointerException("windows");
    starts = new long[windows.size()];
    ends = new long[windows.size()];
    for (int index = 0; index < windows.size(); index++) {
      MaintenanceInterval window = windows.get(index);
      starts[index] = window.startMinute;
      ends[index] = window.endMinute;
    }
  }

  long fit(long earliestStart, long occupiedMinutes) {
    if (earliestStart < 0L || occupiedMinutes <= 0L) {
      throw new IllegalArgumentException("invalid machine interval");
    }
    long candidate = earliestStart;
    int index = firstEndingAfter(candidate);
    while (index < starts.length) {
      long candidateEnd =
          Math.addExact(candidate, occupiedMinutes);
      if (candidateEnd <= starts[index]) {
        return candidate;
      }
      if (candidate < ends[index]) {
        candidate = ends[index];
      }
      index++;
    }
    return candidate;
  }

  private int firstEndingAfter(long minute) {
    int low = 0;
    int high = ends.length;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (ends[middle] <= minute) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }

  boolean matches(List<MaintenanceInterval> windows) {
    if (windows == null || windows.size() != starts.length) {
      return false;
    }
    for (int index = 0; index < starts.length; index++) {
      MaintenanceInterval window = windows.get(index);
      if (window == null
          || starts[index] != window.startMinute
          || ends[index] != window.endMinute) {
        return false;
      }
    }
    return true;
  }
}
