package com.hgtech.soma.examples.scheduler.result;

import com.hgtech.soma.examples.scheduler.support.StableHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 调度结果的稳定、物理顺序无关 checksum。 */
public final class ScheduleChecksum {
  private ScheduleChecksum() {
  }

  public static String compute(List<ScheduledOperation> assignments) {
    ArrayList<ScheduledOperation> ordered =
        new ArrayList<ScheduledOperation>(assignments);
    Collections.sort(ordered, IDENTITY_ORDER);
    StableHash hash = new StableHash()
        .addString("industrial-scheduler-result-v1")
        .addInt(ordered.size());
    for (ScheduledOperation assignment : ordered) {
      hash.addLong(assignment.jobId)
          .addLong(assignment.operationId)
          .addLong(assignment.machineId)
          .addLong(assignment.resourceId)
          .addLong(assignment.setupFamilyId)
          .addLong(assignment.setupStartMinute)
          .addLong(assignment.setupMinutes)
          .addLong(assignment.transportMinutes)
          .addLong(assignment.startMinute)
          .addLong(assignment.processingMinutes)
          .addLong(assignment.endMinute)
          .addLong(assignment.dueMinute)
          .addInt(assignment.priority);
    }
    return hash.finishHex();
  }

  static final Comparator<ScheduledOperation> IDENTITY_ORDER =
      new Comparator<ScheduledOperation>() {
        @Override
        public int compare(ScheduledOperation left,
                           ScheduledOperation right) {
          int result = Long.compare(left.jobId, right.jobId);
          if (result != 0) return result;
          return Long.compare(left.operationId, right.operationId);
        }
      };
}
