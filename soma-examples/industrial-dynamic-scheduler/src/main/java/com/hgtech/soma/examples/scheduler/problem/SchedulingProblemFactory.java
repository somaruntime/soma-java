package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.config.ProblemGenerationConfig;

/** 从显式配置创建 detached SchedulingProblem。 */
public interface SchedulingProblemFactory {
  SchedulingProblem create(ProblemGenerationConfig config);
}
