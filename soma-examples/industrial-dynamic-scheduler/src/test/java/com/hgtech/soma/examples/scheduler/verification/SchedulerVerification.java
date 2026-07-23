package com.hgtech.soma.examples.scheduler.verification;

import com.hgtech.soma.examples.scheduler.config.ProblemConfigLoader;
import com.hgtech.soma.examples.scheduler.config.ProblemGenerationConfig;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.fixture.SchedulingProblemFixtures;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemFactory;
import com.hgtech.soma.examples.scheduler.problem.SyntheticSchedulingProblemFactory;
import com.hgtech.soma.examples.scheduler.result.ScheduleChecksum;
import com.hgtech.soma.examples.scheduler.result.ScheduledOperation;
import com.hgtech.soma.examples.scheduler.result.ScheduleResult;
import com.hgtech.soma.examples.scheduler.result.ScheduleValidator;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSession;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSolver;
import com.hgtech.soma.examples.scheduler.solver.SomaSchedulingSolver;
import com.hgtech.soma.examples.scheduler.oracle.TinyScheduleOracle;

import java.util.ArrayList;
import java.util.Collections;

/** 无 testkit/JUnit 依赖的独立 consumer correctness/long-run entrypoint。 */
public final class SchedulerVerification {
  private SchedulerVerification() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "correctness" : args[0];
    ProblemGenerationConfig config = ProblemConfigLoader.load(selector);
    SchedulingProblemFactory factory =
        new SyntheticSchedulingProblemFactory();
    SchedulingProblem first = factory.create(config);
    SchedulingProblem repeat = factory.create(config);
    require(first.checksum().equals(repeat.checksum()),
        "generator is not deterministic");

    Run run = execute(first);
    Run repeatedRun = execute(repeat);
    require(run.result.resultChecksum.equals(
        repeatedRun.result.resultChecksum), "runtime result is not replayable");

    ArrayList<ScheduledOperation> reversed =
        new ArrayList<ScheduledOperation>(run.result.assignments());
    Collections.reverse(reversed);
    require(ScheduleChecksum.compute(run.result.assignments()).equals(
        ScheduleChecksum.compute(reversed)),
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
        + " operations=" + run.result.assignmentCount()
        + " inputChecksum=" + first.checksum()
        + " resultChecksum=" + run.result.resultChecksum
        + " oracleChecksum=" + oracleChecksum
        + " events=" + run.result.processedEvents
        + " claimAllowed=false");
  }

  private static Run execute(SchedulingProblem problem) {
    SchedulingSolver solver = new SomaSchedulingSolver();
    SchedulingSession session = solver.prepare(problem);
    try {
      ScheduleResult result = session.solve();
      ScheduleValidator.validate(problem, result);
      require(result.diagnostics.assignmentKeyCount == problem.operationCount(),
          "key traversal did not cover every assignment");
      boolean oneShotRejected = false;
      try {
        session.solve();
      } catch (IllegalStateException expected) {
        oneShotRejected = true;
      }
      require(oneShotRejected, "one-shot solver accepted a second run");
      return new Run(result);
    } finally {
      session.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Run {
    final ScheduleResult result;

    Run(ScheduleResult result) {
      this.result = result;
    }
  }
}
