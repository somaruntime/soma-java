package io.github.somaruntime.soma.examples.scheduler.solver;

import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblem;
import io.github.somaruntime.soma.examples.scheduler.runtime.SchedulerRuntime;
import io.github.somaruntime.soma.examples.scheduler.runtime.SchedulerRuntimeFactory;
import io.github.somaruntime.soma.examples.scheduler.schema.generated.OperationAssignmentTable;
import io.github.somaruntime.soma.runtime.IndexSnapshot;
import io.github.somaruntime.soma.runtime.SomaGroupState;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;

/** application aggregate 内部的 Index/lifecycle 负路径证据。 */
public final class SchedulerRuntimeTestAccess {
  private SchedulerRuntimeTestAccess() {
  }

  public static void verify(SchedulingProblem problem) {
    SchedulerRuntime runtime =
        new SchedulerRuntimeFactory().create(problem);
    boolean closed = false;
    try {
      require(runtime.runtimeMetadata().explicit()
              && runtime.runtimeMetadata().members().size() == 9
              && runtime.runtimeMetadata().currentTableInstances() == 9L,
          "scheduler explicit Group topology");
      final OperationAssignmentTable assignments = runtime.assignments();
      IndexSnapshot beforeMutation =
          assignments.indexSnapshot();
      IndexSnapshot wrongSource = runtime.jobs().indexSnapshot();
      DispatchSummary result = new DispatchEngine(runtime).solve();
      if (result.assignments != problem.operationCount()) {
        throw new IllegalStateException("runtime check solve did not complete");
      }
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          assignments.requireCurrent(beforeMutation);
        }
      }, "stale assignment snapshot");
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          assignments.requireCurrent(wrongSource);
        }
      }, "wrong-source snapshot");
      runtime.close();
      closed = true;
      require(runtime.runtimeMetadata().state()
              == SomaGroupState.RELEASED
              && runtime.runtimeMetadata().currentStructuralBytes() == 0L
              && runtime.runtimeMetadata().currentTableInstances() == 0L,
          "scheduler Group terminal snapshot");
      expectRuntimeFailure(new Action() {
        @Override
        public void run() {
          assignments.size();
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

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private interface Action {
    void run();
  }
}
