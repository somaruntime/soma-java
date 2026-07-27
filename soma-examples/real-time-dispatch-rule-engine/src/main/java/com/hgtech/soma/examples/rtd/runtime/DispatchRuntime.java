package com.hgtech.soma.examples.rtd.runtime;

import com.hgtech.soma.examples.rtd.feed.WorkArrivalDelta;
import com.hgtech.soma.examples.rtd.feed.WorkItem;
import com.hgtech.soma.examples.rtd.schema.WorkId;
import com.hgtech.soma.examples.rtd.schema.WorkState;
import com.hgtech.soma.examples.rtd.schema.WorkStatus;
import com.hgtech.soma.examples.rtd.schema.generated.ResourceStateTable;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateDelta;
import com.hgtech.soma.examples.rtd.schema.generated.WorkStateTable;

/** 一次 dispatch horizon 的两张 live Table 与 fail-stop lifecycle Owner。 */
public final class DispatchRuntime implements AutoCloseable {
  private static final int OPEN = 0;
  private static final int FAULTED = 1;
  private static final int CLOSED = 2;

  private final String inputChecksum;
  private final WorkStateTable workStates;
  private final ResourceStateTable resourceStates;
  private int state = OPEN;
  private long currentMinute = -1L;

  DispatchRuntime(
      String inputChecksum,
      WorkStateTable workStates,
      ResourceStateTable resourceStates) {
    if (inputChecksum == null) {
      throw new NullPointerException("inputChecksum");
    }
    if (workStates == null) throw new NullPointerException("workStates");
    if (resourceStates == null) {
      throw new NullPointerException("resourceStates");
    }
    this.inputChecksum = inputChecksum;
    this.workStates = workStates;
    this.resourceStates = resourceStates;
  }

  public void beginCycle(long minute) {
    requireOpen();
    if (minute < 0L || minute <= currentMinute) {
      throw new IllegalArgumentException(
          "cycle minute must be strictly increasing");
    }
    currentMinute = minute;
  }

  public void apply(WorkArrivalDelta arrivals) {
    requireOpen();
    if (arrivals == null) throw new NullPointerException("arrivals");
    if (arrivals.size() == 0) return;
    WorkStateDelta delta =
        new WorkStateDelta(arrivals.size())
            .expectStructuralEpoch(workStates.structuralEpoch());
    for (WorkItem item : arrivals.items()) {
      if (item.releaseMinute() > currentMinute) {
        throw new IllegalArgumentException(
            "work arrival exceeds current cycle");
      }
      WorkState state = new WorkState();
      state.workId = new WorkId(item.id());
      state.capability = item.capability();
      state.releaseMinute = item.releaseMinute();
      state.dueMinute = item.dueMinute();
      state.priority = item.priority();
      state.processingMinutes = item.processingMinutes();
      state.status = WorkStatus.PENDING;
      state.version = 0L;
      delta.insert(state);
    }
    workStates.applyDelta(delta);
  }

  public WorkStateTable workStates() {
    requireOpen();
    return workStates;
  }

  public ResourceStateTable resourceStates() {
    requireOpen();
    return resourceStates;
  }

  public String inputChecksum() { return inputChecksum; }
  public long currentMinute() {
    requireOpen();
    return currentMinute;
  }
  public boolean isClosed() { return state == CLOSED; }

  public <T extends Throwable> T fail(T primary) {
    if (primary == null) throw new NullPointerException("primary");
    if (state == CLOSED) return primary;
    state = FAULTED;
    release(primary);
    return primary;
  }

  @Override
  public void close() {
    if (state == CLOSED) return;
    Throwable failure = null;
    try {
      resourceStates.release();
    } catch (Throwable releaseFailure) {
      failure = releaseFailure;
    }
    try {
      workStates.release();
    } catch (Throwable releaseFailure) {
      if (failure == null) {
        failure = releaseFailure;
      } else if (failure != releaseFailure) {
        failure.addSuppressed(releaseFailure);
      }
    } finally {
      state = CLOSED;
    }
    rethrow(failure);
  }

  private void release(Throwable primary) {
    try {
      close();
    } catch (Throwable cleanup) {
      if (cleanup != primary) primary.addSuppressed(cleanup);
    }
  }

  private void requireOpen() {
    if (state != OPEN) {
      throw new IllegalStateException(
          state == FAULTED
              ? "dispatch runtime is faulted"
              : "dispatch runtime is closed");
    }
  }

  private static void rethrow(Throwable failure) {
    if (failure == null) return;
    if (failure instanceof RuntimeException) {
      throw (RuntimeException) failure;
    }
    if (failure instanceof Error) throw (Error) failure;
    throw new IllegalStateException("runtime release failed", failure);
  }
}
