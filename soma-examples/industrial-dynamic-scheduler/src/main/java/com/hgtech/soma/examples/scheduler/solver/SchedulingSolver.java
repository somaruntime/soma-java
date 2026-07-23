package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.result.ScheduleResult;

/** 工业调度应用的 canonical solver facade。 */
public interface SchedulingSolver {
  ScheduleResult solve(SchedulingProblem problem);

  SchedulingSession prepare(SchedulingProblem problem);
}
