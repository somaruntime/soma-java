package com.hgtech.soma.examples.scheduler.runtime;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.SomaRuntimeException;

/** application aggregate 内部的 Index/lifecycle 负路径证据。 */
public final class SchedulerRuntimeChecks {
  private SchedulerRuntimeChecks() {
  }

  public static void verify(SchedulingProblem problem) {
    SchedulerRuntime runtime = SchedulerRuntimeBootstrap.load(problem);
    boolean closed = false;
    try {
      IndexSnapshot beforeMutation = runtime.assignments.indexSnapshot();
      IndexSnapshot wrongSource = runtime.jobs.indexSnapshot();
      DispatchSummary result = new IndustrialScheduler(runtime).solve();
      if (result.assignments != problem.operationCount()) {
        throw new IllegalStateException("runtime check solve did not complete");
      }
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          runtime.assignments.requireCurrent(beforeMutation);
        }
      }, "stale assignment snapshot");
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          runtime.assignments.requireCurrent(wrongSource);
        }
      }, "wrong-source snapshot");
      runtime.close();
      closed = true;
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          runtime.assignments.size();
        }
      }, "released table access");
    } finally {
      if (!closed) runtime.close();
    }
  }

  private static void expectRuntimeFailure(Action action, String label) {
    boolean rejected = false;
    try {
      action.run();
    } catch (SomaRuntimeException expected) {
      rejected = true;
    }
    if (!rejected) {
      throw new IllegalStateException(label + " was accepted");
    }
  }

  private interface Action {
    void run();
  }
}
