package com.hgtech.soma.examples.fjsp;

/**
 * FJSP 教学入口：输入问题 -> SOMA runtime instance -> FCFS + SPT solver -> 领域结果。
 */
public final class FjspScenario {
  private FjspScenario() {
  }

  public static void main(String[] args) {
    System.out.println(run());
  }

  public static FjspSolveResult run() {
    FjspProblem problem = FjspProblem.teachingExample();
    try (FjspInstance instance = FjspInstanceFactory.create(problem)) {
      FjspSolver solver = new FjspSolver(instance, new FcfsSptDispatchRule());
      return solver.solve();
    }
  }
}
