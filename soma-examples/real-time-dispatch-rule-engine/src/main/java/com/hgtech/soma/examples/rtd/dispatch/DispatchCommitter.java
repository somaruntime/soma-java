package com.hgtech.soma.examples.rtd.dispatch;

import com.hgtech.soma.examples.rtd.result.DispatchCommand;
import com.hgtech.soma.examples.rtd.runtime.DispatchRuntime;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateMutator;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateTable;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateMutator;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateTable;
import com.hgtech.soma.runtime.BooleanColumnView;
import com.hgtech.soma.runtime.EnumColumnView;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 预检 detached command batch，并顺序提交两个独立 root Table。 */
final class DispatchCommitter {
  void commit(
      DispatchRuntime runtime, List<DispatchCommand> commands) {
    if (runtime == null) throw new NullPointerException("runtime");
    if (commands == null) throw new NullPointerException("commands");
    preflight(runtime, commands);
    for (DispatchCommand command : commands) {
      WorkStateTable work = runtime.workStates();
      WorkStateMutator workMutation =
          work.mutateAt(work.requireIndex(command.workId()));
      workMutation
          .setStatus(WorkStatus.DISPATCHED)
          .setVersion(Math.addExact(
              command.expectedWorkVersion(), 1L))
          .commit();

      ResourceStateTable resources = runtime.resourceStates();
      ResourceStateMutator resourceMutation =
          resources.mutateAt(
              resources.requireIndex(command.resourceId()));
      resourceMutation
          .setAvailableMinute(command.completionMinute())
          .setVersion(Math.addExact(
              command.expectedResourceVersion(), 1L))
          .commit();
    }
  }

  private static void preflight(
      DispatchRuntime runtime, List<DispatchCommand> commands) {
    WorkStateTable work = runtime.workStates();
    ResourceStateTable resources = runtime.resourceStates();
    Set<Long> workIds = new HashSet<Long>();
    Set<Long> resourceIds = new HashSet<Long>();
    IntColumnView workCapability = work.capabilityColumn();
    LongColumnView release = work.releaseMinuteColumn();
    LongColumnView processing = work.processingMinutesColumn();
    EnumColumnView<WorkStatus> status = work.statusColumn();
    LongColumnView workVersion = work.versionColumn();
    IntColumnView resourceCapability = resources.capabilityColumn();
    LongColumnView available = resources.availableMinuteColumn();
    BooleanColumnView enabled = resources.enabledColumn();
    LongColumnView resourceVersion = resources.versionColumn();
    try {
      for (DispatchCommand command : commands) {
        if (command == null) throw new NullPointerException("command");
        if (!workIds.add(Long.valueOf(command.workId()))) {
          throw new IllegalStateException(
              "duplicate work command: " + command.workId());
        }
        if (!resourceIds.add(Long.valueOf(command.resourceId()))) {
          throw new IllegalStateException(
              "duplicate resource command: " + command.resourceId());
        }
        int workIndex = work.requireIndex(command.workId());
        int resourceIndex =
            resources.requireIndex(command.resourceId());
        long expectedCompletion = Math.addExact(
            command.startMinute(),
            processing.getLong(workIndex));
        if (command.issueMinute() != runtime.currentMinute()
            || status.get(workIndex) != WorkStatus.PENDING
            || release.getLong(workIndex) > command.issueMinute()
            || workCapability.getInt(workIndex)
                != command.capability()
            || workVersion.getLong(workIndex)
                != command.expectedWorkVersion()
            || !enabled.getBoolean(resourceIndex)
            || resourceCapability.getInt(resourceIndex)
                != command.capability()
            || available.getLong(resourceIndex)
                > command.startMinute()
            || resourceVersion.getLong(resourceIndex)
                != command.expectedResourceVersion()
            || expectedCompletion != command.completionMinute()) {
          throw new IllegalStateException(
              "dispatch command failed preflight");
        }
      }
    } finally {
      resourceVersion.close();
      enabled.close();
      available.close();
      resourceCapability.close();
      workVersion.close();
      status.close();
      processing.close();
      release.close();
      workCapability.close();
    }
  }
}
