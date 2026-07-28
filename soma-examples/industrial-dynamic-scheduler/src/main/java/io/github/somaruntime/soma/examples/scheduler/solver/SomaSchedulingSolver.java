package io.github.somaruntime.soma.examples.scheduler.solver;

import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblem;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleResult;
import io.github.somaruntime.soma.examples.scheduler.runtime.SchedulerRuntimeFactory;

/** 使用 SOMA columnar runtime 的正式 solver 实现。 */
public final class SomaSchedulingSolver implements SchedulingSolver {
  @Override
  public ScheduleResult solve(SchedulingProblem problem) {
    SchedulingSession session = prepare(problem);
    try {
      return session.solve();
    } finally {
      session.close();
    }
  }

  @Override
  public SchedulingSession prepare(SchedulingProblem problem) {
    if (problem == null) throw new NullPointerException("problem");
    return new SomaSchedulingSession(
        new SchedulerRuntimeFactory().create(problem));
  }
}
