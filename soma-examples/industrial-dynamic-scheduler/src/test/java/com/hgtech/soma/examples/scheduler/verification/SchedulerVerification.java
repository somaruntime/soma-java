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
import com.hgtech.soma.examples.scheduler.runtime.SchedulerProjectionTestAccess;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSession;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSolver;
import com.hgtech.soma.examples.scheduler.solver.SchedulerExecutionTestAccess;
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
    SchedulerProjectionTestAccess.verify(first);

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
      SchedulingProblemFixtures.verifyTimeOverflowRejected();
      SchedulingProblemFixtures.verifySemanticIdentity();
      SchedulingProblemFixtures.verifyResultClaimsClosed();
      SchedulerRuntimeChecks.verify(
          SchedulingProblemFixtures.tinyOracle());
    }
    System.out.println("scheduler-verification: profile=" + selector
        + " operations=" + run.result.assignmentCount()
        + " inputChecksum=" + first.checksum()
        + " resultChecksum=" + run.result.resultChecksum
        + " oracleChecksum=" + oracleChecksum
        + " events=" + run.evidence.processedEvents
        + " claimAllowed=false");
  }

  private static Run execute(SchedulingProblem problem) {
    SchedulingSolver solver = new SomaSchedulingSolver();
    SchedulingSession session = solver.prepare(problem);
    try {
      ScheduleResult result = session.solve();
      ScheduleValidator.validate(problem, result);
      SchedulerExecutionTestAccess.Evidence evidence =
          SchedulerExecutionTestAccess.capture(session);
      require(evidence.assignmentKeyCount == problem.operationCount(),
          "key traversal did not cover every assignment");
      require(!evidence.dataFlowDefinitionIdentity.isEmpty()
              && !evidence.dataFlowTemplateIdentity.isEmpty()
              && evidence.dataFlowBoundSources == 1L
              && evidence.dataFlowScanned >= problem.operationCount()
              && evidence.dataFlowMatched >= problem.operationCount()
              && evidence.dataFlowOutputElements >= 3L,
          "assignment summary DataFlow evidence is incomplete");
      boolean oneShotRejected = false;
      try {
        session.solve();
      } catch (IllegalStateException expected) {
        oneShotRejected = true;
      }
      require(oneShotRejected, "one-shot solver accepted a second run");
      return new Run(result, evidence);
    } finally {
      session.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Run {
    final ScheduleResult result;
    final SchedulerExecutionTestAccess.Evidence evidence;

    Run(ScheduleResult result,
        SchedulerExecutionTestAccess.Evidence evidence) {
      this.result = result;
      this.evidence = evidence;
    }
  }
}
