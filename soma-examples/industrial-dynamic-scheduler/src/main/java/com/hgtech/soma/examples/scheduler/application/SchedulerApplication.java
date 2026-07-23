package com.hgtech.soma.examples.scheduler.application;

import com.hgtech.soma.examples.scheduler.config.ProblemConfigLoader;
import com.hgtech.soma.examples.scheduler.config.ProblemGenerationConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemFactory;
import com.hgtech.soma.examples.scheduler.problem.SyntheticSchedulingProblemFactory;
import com.hgtech.soma.examples.scheduler.result.ScheduleResult;
import com.hgtech.soma.examples.scheduler.result.ScheduleValidator;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSession;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSolver;
import com.hgtech.soma.examples.scheduler.solver.SomaSchedulingSolver;

import java.util.Arrays;

/** 普通 Java 8 consumer 的 headless CLI journey。 */
public final class SchedulerApplication {
  private SchedulerApplication() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    String[] overrides = args.length <= 1
        ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    ProblemGenerationConfig config =
        ProblemConfigLoader.load(selector, overrides);
    SchedulingProblemFactory problemFactory =
        new SyntheticSchedulingProblemFactory();
    long generationStart = System.nanoTime();
    SchedulingProblem problem = problemFactory.create(config);
    long generationNanos = System.nanoTime() - generationStart;
    SchedulingSolver solver = new SomaSchedulingSolver();
    SchedulingSession session = null;
    try {
      long bootstrapStart = System.nanoTime();
      session = solver.prepare(problem);
      long bootstrapNanos = System.nanoTime() - bootstrapStart;
      long solveStart = System.nanoTime();
      ScheduleResult result = session.solve();
      long solveNanos = System.nanoTime() - solveStart;
      ScheduleValidator.ValidationSummary validated =
          ScheduleValidator.validate(problem, result);
      System.out.print(config.canonicalText());
      System.out.println("config.checksum=" + config.checksum());
      System.out.println("input.checksum=" + problem.checksum());
      System.out.println("result.checksum=" + validated.checksum);
      System.out.println("assignments=" + validated.assignments);
      System.out.println("makespan.minute=" + validated.makespanMinute);
      System.out.println("tardiness.minute="
          + validated.totalTardinessMinutes);
      System.out.println("weighted.tardiness=" + validated.weightedTardiness);
      System.out.println("events.processed=" + result.processedEvents);
      System.out.println("generation.nanos=" + generationNanos);
      System.out.println("bootstrap.nanos=" + bootstrapNanos);
      System.out.println("solve.nanos=" + solveNanos);
      System.out.println("claimAllowed=false");
    } finally {
      if (session != null) session.close();
    }
  }
}
