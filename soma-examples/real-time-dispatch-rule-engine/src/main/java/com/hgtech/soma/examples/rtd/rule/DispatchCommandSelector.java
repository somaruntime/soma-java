package com.hgtech.soma.examples.rtd.rule;

import com.hgtech.soma.dataflow.GroupedLongResult;
import com.hgtech.soma.dataflow.JoinedIndexResult;
import com.hgtech.soma.examples.rtd.result.DispatchCommand;
import com.hgtech.soma.examples.rtd.runtime.DispatchRuntime;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateTable;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateTable;
import com.hgtech.soma.examples.rtd.support.StableHash;
import com.hgtech.soma.runtime.BooleanColumnView;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.ArrayList;
import java.util.List;

/** 在一次同步只读批次中把 joined current Index 复制为 detached command。 */
final class DispatchCommandSelector {
  Selection select(
      DispatchRuntime runtime,
      JoinedIndexResult pairs,
      GroupedLongResult demand) {
    WorkStateTable work = runtime.workStates();
    ResourceStateTable resources = runtime.resourceStates();
    if (pairs.leftStructuralEpoch() != work.structuralEpoch()
        || pairs.rightStructuralEpoch() != resources.structuralEpoch()) {
      throw new IllegalStateException(
          "joined indexes became stale before command selection");
    }
    boolean[] selectedWork = new boolean[work.size()];
    boolean[] selectedResource = new boolean[resources.size()];
    ArrayList<DispatchCommand> commands =
        new ArrayList<DispatchCommand>(
            Math.min(work.size(), resources.size()));

    LongColumnView workId = work.workIdValueColumn();
    IntColumnView workCapability = work.capabilityColumn();
    LongColumnView workRelease = work.releaseMinuteColumn();
    IntColumnView workPriority = work.priorityColumn();
    LongColumnView processing = work.processingMinutesColumn();
    EnumColumnView<WorkStatus> workStatus = work.statusColumn();
    LongColumnView workVersion = work.versionColumn();
    LongColumnView resourceId = resources.resourceIdValueColumn();
    IntColumnView resourceCapability = resources.capabilityColumn();
    LongColumnView available = resources.availableMinuteColumn();
    BooleanColumnView enabled = resources.enabledColumn();
    LongColumnView resourceVersion = resources.versionColumn();
    try {
      for (int position = 0; position < pairs.size(); position++) {
        int left = pairs.leftIndexAt(position);
        int right = pairs.rightIndexAt(position);
        if (selectedWork[left] || selectedResource[right]) continue;
        int capability = workCapability.getInt(left);
        if (workStatus.get(left) != WorkStatus.PENDING
            || workRelease.getLong(left) > runtime.currentMinute()
            || !enabled.getBoolean(right)
            || available.getLong(right) > runtime.currentMinute()
            || resourceCapability.getInt(right) != capability) {
          throw new IllegalStateException(
              "rule output violates dispatch precondition");
        }
        long start = Math.max(
            runtime.currentMinute(), available.getLong(right));
        long completion = Math.addExact(
            start, processing.getLong(left));
        commands.add(new DispatchCommand(
            workId.getLong(left),
            resourceId.getLong(right),
            capability,
            runtime.currentMinute(),
            start,
            completion,
            workPriority.getInt(left),
            workVersion.getLong(left),
            resourceVersion.getLong(right)));
        selectedWork[left] = true;
        selectedResource[right] = true;
      }
      return new Selection(
          commands,
          demandChecksum(demand, workCapability));
    } finally {
      resourceVersion.close();
      enabled.close();
      available.close();
      resourceCapability.close();
      resourceId.close();
      workVersion.close();
      workStatus.close();
      processing.close();
      workPriority.close();
      workRelease.close();
      workCapability.close();
      workId.close();
    }
  }

  private static String demandChecksum(
      GroupedLongResult demand, IntColumnView capability) {
    StableHash hash = new StableHash()
        .addString("rtd-demand-groups-v1")
        .addInt(demand.size());
    long total = 0L;
    for (int group = 0; group < demand.size(); group++) {
      long count = demand.valueAt(group);
      total = Math.addExact(total, count);
      hash.addInt(capability.getInt(
              demand.representativeIndexAt(group)))
          .addLong(count);
    }
    return hash.addLong(total).finishHex();
  }

  static final class Selection {
    final List<DispatchCommand> commands;
    final String demandChecksum;

    Selection(
        List<DispatchCommand> commands, String demandChecksum) {
      this.commands = commands;
      this.demandChecksum = demandChecksum;
    }
  }
}
