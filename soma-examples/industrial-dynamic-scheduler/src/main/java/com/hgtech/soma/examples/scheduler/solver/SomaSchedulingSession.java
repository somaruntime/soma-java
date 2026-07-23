package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.result.ScheduleResult;
import com.hgtech.soma.examples.scheduler.result.ScheduleResultAssembler;
import com.hgtech.soma.examples.scheduler.runtime.DispatchSummary;
import com.hgtech.soma.examples.scheduler.runtime.IndustrialScheduler;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;

/** package-private session implementation，独占一个 SchedulerRuntime。 */
final class SomaSchedulingSession implements SchedulingSession {
  private SchedulerRuntime runtime;
  private boolean started;

  SomaSchedulingSession(SchedulerRuntime runtime) {
    this.runtime = runtime;
  }

  @Override
  public ScheduleResult solve() {
    if (started) throw new IllegalStateException("session is one-shot");
    if (runtime == null) throw new IllegalStateException("session is closed");
    started = true;
    try {
      DispatchSummary summary = new IndustrialScheduler(runtime).solve();
      return ScheduleResultAssembler.assemble(runtime, summary);
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    if (runtime == null) return;
    SchedulerRuntime owned = runtime;
    runtime = null;
    owned.close();
  }
}
