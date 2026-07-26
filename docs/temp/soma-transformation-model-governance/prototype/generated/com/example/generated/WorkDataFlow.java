package com.example.generated;

import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.GeneratedBinding;
import com.hgtech.soma.dataflow.prototype.PrototypeDataFlow.SourceSlot;
import java.util.List;

/** 单个 generated Table companion 的 Temporary 替身。 */
public final class WorkDataFlow {
  public static final String SCHEMA = "work-schema-v1";
  public static final SourceSlot<WorkBinding> SOURCE =
      new SourceSlot<WorkBinding>("work", SCHEMA);
  public static final SourceSlot<WorkBinding> ALIAS =
      new SourceSlot<WorkBinding>("workAlias", SCHEMA);

  private WorkDataFlow() {
  }

  public static final class WorkBinding implements GeneratedBinding {
    private final long aggregateId;
    private final int[] values;
    private final List<Long> lifecycleEvents;
    private boolean failAcquire;
    private boolean acquired;
    private int acquireCount;
    private int releaseCount;

    public WorkBinding(long aggregateId, int[] values, List<Long> lifecycleEvents) {
      this.aggregateId = aggregateId;
      this.values = values;
      this.lifecycleEvents = lifecycleEvents;
    }

    public WorkBinding failAcquire() {
      failAcquire = true;
      return this;
    }

    @Override
    public long aggregateInstanceId() {
      return aggregateId;
    }

    @Override
    public String schemaIdentity() {
      return SCHEMA;
    }

    @Override
    public int[] packedValues() {
      return values;
    }

    @Override
    public void acquire() {
      if (failAcquire) {
        throw new IllegalStateException("acquire failure " + aggregateId);
      }
      if (acquired) {
        throw new IllegalStateException("already acquired " + aggregateId);
      }
      acquired = true;
      acquireCount++;
      lifecycleEvents.add(Long.valueOf(aggregateId));
    }

    @Override
    public void release() {
      if (!acquired) {
        throw new IllegalStateException("not acquired " + aggregateId);
      }
      acquired = false;
      releaseCount++;
      lifecycleEvents.add(Long.valueOf(-aggregateId));
    }

    public int acquireCount() {
      return acquireCount;
    }

    public int releaseCount() {
      return releaseCount;
    }
  }
}
