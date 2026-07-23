package com.hgtech.soma.examples.scheduler.evidence;

import com.hgtech.soma.examples.scheduler.config.SchedulerConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemFixtures;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemGenerator;
import com.hgtech.soma.examples.scheduler.runtime.IndustrialScheduler;
import com.hgtech.soma.examples.scheduler.runtime.ScheduleResult;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntimeBootstrap;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntimeChecks;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;
import com.hgtech.soma.examples.scheduler.validation.ScheduleValidator;
import com.hgtech.soma.examples.scheduler.validation.TinyScheduleOracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 无 testkit/JUnit 依赖的独立 consumer correctness/long-run entrypoint。 */
public final class SchedulerVerification {
  private SchedulerVerification() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "correctness" : args[0];
    SchedulerConfig config = SchedulerConfig.load(selector);
    SchedulingProblem first = SchedulingProblemGenerator.generate(config);
    SchedulingProblem repeat = SchedulingProblemGenerator.generate(config);
    require(first.checksum().equals(repeat.checksum()),
        "generator is not deterministic");

    Run run = execute(first);
    Run repeatedRun = execute(repeat);
    require(run.result.resultChecksum.equals(
        repeatedRun.result.resultChecksum), "runtime result is not replayable");

    ArrayList<OperationAssignment> reversed =
        new ArrayList<OperationAssignment>(run.assignments);
    Collections.reverse(reversed);
    require(ScheduleValidator.assignmentChecksum(run.assignments).equals(
        ScheduleValidator.assignmentChecksum(reversed)),
        "result checksum depends on physical order");

    String oracleChecksum = "not-run";
    if ("correctness".equals(selector)
        || selector.endsWith("correctness.properties")) {
      oracleChecksum = TinyScheduleOracle.verify();
      SchedulingProblemFixtures.verifyInvalidInputRejected();
      SchedulerRuntimeChecks.verify(
          SchedulingProblemFixtures.tinyOracle());
    }
    System.out.println("scheduler-verification: profile=" + selector
        + " operations=" + run.result.assignments
        + " inputChecksum=" + first.checksum()
        + " resultChecksum=" + run.result.resultChecksum
        + " oracleChecksum=" + oracleChecksum
        + " events=" + run.result.processedEvents
        + " claimAllowed=false");
  }

  private static Run execute(SchedulingProblem problem) {
    SchedulerRuntime runtime = SchedulerRuntimeBootstrap.load(problem);
    try {
      IndustrialScheduler scheduler = new IndustrialScheduler(runtime);
      ScheduleResult result = scheduler.solve();
      List<OperationAssignment> assignments = runtime.exportAssignments();
      ScheduleValidator.validate(problem, assignments, result);
      require(runtime.assignmentKeyCount() == problem.operationCount(),
          "key traversal did not cover every assignment");
      boolean oneShotRejected = false;
      try {
        scheduler.solve();
      } catch (IllegalStateException expected) {
        oneShotRejected = true;
      }
      require(oneShotRejected, "one-shot solver accepted a second run");
      return new Run(result,
          new ArrayList<OperationAssignment>(assignments));
    } finally {
      runtime.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Run {
    final ScheduleResult result;
    final List<OperationAssignment> assignments;

    Run(ScheduleResult result, List<OperationAssignment> assignments) {
      this.result = result;
      this.assignments = assignments;
    }
  }
}
