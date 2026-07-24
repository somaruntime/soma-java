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
    boolean moved;
    do {
      moved = false;
      long candidateEnd = Math.addExact(candidate, occupiedMinutes);
      long next = candidate;
      for (int index = 0; index < starts.length; index++) {
        if (candidate < ends[index] && candidateEnd > starts[index]) {
          next = Math.max(next, ends[index]);
          moved = true;
        }
      }
      candidate = next;
    } while (moved);
    return candidate;
  }
}
