package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.schema.MachineId;
import com.hgtech.soma.examples.scheduler.schema.generated.MaintenanceWindowTable;
import com.hgtech.soma.runtime.LongColumnView;

/** 对 owned maintenance child 做物理顺序无关的连续区间定位。 */
final class MachineCalendar {
  private MachineCalendar() {
  }

  static long fit(SchedulerRuntime runtime, MachineId machine,
                  long earliestStart, long occupiedMinutes) {
    if (earliestStart < 0L || occupiedMinutes <= 0L) {
      throw new IllegalArgumentException("invalid machine interval");
    }
    MaintenanceWindowTable windows =
        runtime.machineDefinitions().maintenanceWindows(machine);
    LongColumnView starts = windows.startMinuteColumn();
    LongColumnView ends = windows.endMinuteColumn();
    try {
      long candidate = earliestStart;
      boolean moved;
      do {
        moved = false;
        long candidateEnd = Math.addExact(candidate, occupiedMinutes);
        long next = candidate;
        for (int index = 0; index < windows.size(); index++) {
          long start = starts.getLong(index);
          long end = ends.getLong(index);
          if (candidate < end && candidateEnd > start) {
            next = Math.max(next, end);
            moved = true;
          }
        }
        candidate = next;
      } while (moved);
      return candidate;
    } finally {
      ends.close();
      starts.close();
    }
  }
}
