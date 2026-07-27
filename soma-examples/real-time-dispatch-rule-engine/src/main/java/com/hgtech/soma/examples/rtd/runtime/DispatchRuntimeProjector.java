package com.hgtech.soma.examples.rtd.runtime;

import com.hgtech.soma.examples.rtd.feed.InitialRuntimeSnapshot;
import com.hgtech.soma.examples.rtd.feed.ResourceSnapshot;
import com.hgtech.soma.examples.rtd.feed.WorkItem;
import com.hgtech.soma.examples.rtd.schema.ResourceId;
import com.hgtech.soma.examples.rtd.schema.WorkId;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateBatch;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateBatch;

/** 将 detached initial snapshot 一次性投影为 live columnar state。 */
final class DispatchRuntimeProjector {
  private DispatchRuntimeProjector() {
  }

  static void project(
      InitialRuntimeSnapshot snapshot, DispatchRuntime runtime) {
    WorkStateBatch work = new WorkStateBatch(snapshot.workCount());
    for (WorkItem item : snapshot.work()) {
      work.addValues(
          new WorkId(item.id()),
          item.capability(),
          item.releaseMinute(),
          item.dueMinute(),
          item.priority(),
          item.processingMinutes(),
          WorkStatus.PENDING,
          0L);
    }
    ResourceStateBatch resources =
        new ResourceStateBatch(snapshot.resourceCount());
    for (ResourceSnapshot resource : snapshot.resources()) {
      resources.addValues(
          new ResourceId(resource.id()),
          resource.capability(),
          resource.availableMinute(),
          resource.enabled(),
          0L);
    }
    runtime.workStates().addBatch(work);
    runtime.resourceStates().addBatch(resources);
  }
}
