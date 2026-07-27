package com.hgtech.soma.examples.rtd.dispatch;

import com.hgtech.soma.examples.rtd.feed.DispatchScenario;
import com.hgtech.soma.examples.rtd.result.DispatchCommand;
import com.hgtech.soma.examples.rtd.runtime.DispatchRuntime;
import com.hgtech.soma.examples.rtd.runtime.DispatchRuntimeFactory;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateTable;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateTable;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

import java.util.Collections;

/** application-owned safe-point effect 与全批次预检的代表性证据。 */
public final class DispatchCommitterVerification {
  private DispatchCommitterVerification() {
  }

  public static void verify(DispatchScenario scenario) {
    DispatchRuntime runtime =
        new DispatchRuntimeFactory().create(scenario);
    try {
      runtime.beginCycle(scenario.cycleAt(0).currentMinute());
      runtime.apply(scenario.cycleAt(0).arrivals());
      DispatchCommand valid = firstCompatibleCommand(runtime);
      DispatchCommand stale = new DispatchCommand(
          valid.workId(),
          valid.resourceId(),
          valid.capability(),
          valid.issueMinute(),
          valid.startMinute(),
          valid.completionMinute(),
          valid.priority(),
          1L,
          valid.expectedResourceVersion());
      try {
        new DispatchCommitter().commit(
            runtime, Collections.singletonList(stale));
        throw new AssertionError("stale command expected");
      } catch (IllegalStateException expected) {
        require(
            runtime.workStates()
                .scanByStatus(WorkStatus.DISPATCHED).count() == 0L,
            "failed preflight changed work state");
        require(
            resourceVersion(runtime, valid.resourceId()) == 0L,
            "failed preflight changed resource state");
      }
      new DispatchCommitter().commit(
          runtime, Collections.singletonList(valid));
      require(
          runtime.workStates()
              .scanByStatus(WorkStatus.DISPATCHED).count() == 1L,
          "valid command did not update work");
      require(
          resourceVersion(runtime, valid.resourceId()) == 1L,
          "valid command did not update resource");
    } finally {
      runtime.close();
    }
  }

  private static DispatchCommand firstCompatibleCommand(
      DispatchRuntime runtime) {
    WorkStateTable work = runtime.workStates();
    ResourceStateTable resources = runtime.resourceStates();
    LongColumnView workId = work.workIdValueColumn();
    IntColumnView workCapability = work.capabilityColumn();
    LongColumnView processing = work.processingMinutesColumn();
    IntColumnView priority = work.priorityColumn();
    LongColumnView resourceId = resources.resourceIdValueColumn();
    IntColumnView resourceCapability = resources.capabilityColumn();
    try {
      for (int left = 0; left < work.size(); left++) {
        for (int right = 0; right < resources.size(); right++) {
          int capability = workCapability.getInt(left);
          if (capability != resourceCapability.getInt(right)) continue;
          long completion = Math.addExact(
              runtime.currentMinute(), processing.getLong(left));
          return new DispatchCommand(
              workId.getLong(left),
              resourceId.getLong(right),
              capability,
              runtime.currentMinute(),
              runtime.currentMinute(),
              completion,
              priority.getInt(left),
              0L,
              0L);
        }
      }
      throw new IllegalStateException("no compatible pair");
    } finally {
      resourceCapability.close();
      resourceId.close();
      priority.close();
      processing.close();
      workCapability.close();
      workId.close();
    }
  }

  private static long resourceVersion(
      DispatchRuntime runtime, long resourceId) {
    LongColumnView version =
        runtime.resourceStates().versionColumn();
    try {
      return version.getLong(
          runtime.resourceStates().requireIndex(resourceId));
    } finally {
      version.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
