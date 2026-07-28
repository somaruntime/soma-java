package io.github.somaruntime.soma.examples.scheduler.problem;

import io.github.somaruntime.soma.examples.scheduler.config.ProblemGenerationConfig;

/** 从显式配置创建 detached SchedulingProblem。 */
public interface SchedulingProblemFactory {
  SchedulingProblem create(ProblemGenerationConfig config);
}
