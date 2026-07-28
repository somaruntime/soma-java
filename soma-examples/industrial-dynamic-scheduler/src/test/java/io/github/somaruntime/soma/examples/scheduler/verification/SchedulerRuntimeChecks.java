package io.github.somaruntime.soma.examples.scheduler.verification;

import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblem;
import io.github.somaruntime.soma.examples.scheduler.solver.SchedulerRuntimeTestAccess;

/** Scheduler runtime 的 lifecycle 与 IndexSnapshot 负路径入口。 */
public final class SchedulerRuntimeChecks {
  private SchedulerRuntimeChecks() {
  }

  public static void verify(SchedulingProblem problem) {
    SchedulerRuntimeTestAccess.verify(problem);
  }
}
