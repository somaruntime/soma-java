package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.result.ScheduleResult;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;

/** package-private session implementation，独占一个 SchedulerRuntime。 */
final class SomaSchedulingSession implements SchedulingSession {
  private SchedulerRuntime runtime;
  private boolean started;
  private SolveEvidence evidence;

  SomaSchedulingSession(SchedulerRuntime runtime) {
    this.runtime = runtime;
  }

  @Override
  public ScheduleResult solve() {
    if (started) throw new IllegalStateException("session is one-shot");
    if (runtime == null) throw new IllegalStateException("session is closed");
    started = true;
    try {
      DispatchEngine engine = new DispatchEngine(runtime);
      DispatchSummary summary = engine.solve();
      ScheduleResult result =
          ScheduleResultAssembler.assemble(runtime, summary);
      evidence = SolveEvidence.capture(
          runtime, summary, engine.summaryEvidence());
      return result;
    } finally {
      close();
    }
  }

  SolveEvidence evidence() {
    if (evidence == null) {
      throw new IllegalStateException(
          "solve evidence is available only after a successful solve");
    }
    return evidence;
  }

  @Override
  public void close() {
    if (runtime == null) return;
    SchedulerRuntime owned = runtime;
    runtime = null;
    owned.close();
  }
}
