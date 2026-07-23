package com.hgtech.soma.examples.scheduler;

import com.hgtech.soma.examples.scheduler.config.SchedulerConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemGenerator;
import com.hgtech.soma.examples.scheduler.runtime.IndustrialScheduler;
import com.hgtech.soma.examples.scheduler.runtime.ScheduleResult;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntimeBootstrap;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;
import com.hgtech.soma.examples.scheduler.validation.ScheduleValidator;

import java.util.Arrays;
import java.util.List;

/** 普通 Java 8 consumer 的 headless CLI journey。 */
public final class SchedulerApplication {
  private SchedulerApplication() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    String[] overrides = args.length <= 1
        ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    SchedulerConfig config = SchedulerConfig.load(selector, overrides);
    long generationStart = System.nanoTime();
    SchedulingProblem problem = SchedulingProblemGenerator.generate(config);
    long generationNanos = System.nanoTime() - generationStart;
    SchedulerRuntime runtime = null;
    try {
      long bootstrapStart = System.nanoTime();
      runtime = SchedulerRuntimeBootstrap.load(problem);
      long bootstrapNanos = System.nanoTime() - bootstrapStart;
      long solveStart = System.nanoTime();
      ScheduleResult result = new IndustrialScheduler(runtime).solve();
      long solveNanos = System.nanoTime() - solveStart;
      List<OperationAssignment> assignments = runtime.exportAssignments();
      ScheduleValidator.ValidationSummary validated =
          ScheduleValidator.validate(problem, assignments, result);
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
      if (runtime != null) runtime.close();
    }
  }
}
