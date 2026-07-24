package com.hgtech.soma.examples.scheduler.fixture;

import com.hgtech.soma.examples.scheduler.problem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.ExternalEventType;
import com.hgtech.soma.examples.scheduler.problem.JobSpec;
import com.hgtech.soma.examples.scheduler.problem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.MachineSpec;
import com.hgtech.soma.examples.scheduler.problem.MaintenanceInterval;
import com.hgtech.soma.examples.scheduler.problem.OperationSpec;
import com.hgtech.soma.examples.scheduler.problem.ResourceSpec;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SetupTimeSpec;
import com.hgtech.soma.examples.scheduler.problem.TransportTimeSpec;
import com.hgtech.soma.examples.scheduler.result.ScheduleResult;
import com.hgtech.soma.examples.scheduler.result.ScheduleValidator;
import com.hgtech.soma.examples.scheduler.solver.SchedulingSolver;
import com.hgtech.soma.examples.scheduler.solver.SomaSchedulingSolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 手算 oracle 与输入失败路径；不进入 production generator。 */
public final class SchedulingProblemFixtures {
  private SchedulingProblemFixtures() {
  }

  public static SchedulingProblem tinyOracle() {
    JobSpec job = new JobSpec(1L, 2L, 3L, 12L, 2, 2);
    MachineSpec firstMachine = new MachineSpec(10L, 100L, 0L,
        Collections.singletonList(new MaintenanceInterval(8L, 12L)));
    MachineSpec secondMachine = new MachineSpec(20L, 200L, 0L,
        Collections.<MaintenanceInterval>emptyList());
    ResourceSpec resource = new ResourceSpec(30L, 1);
    OperationSpec first = new OperationSpec(
        1L, 101L, 0, 100L, 30L, 1,
        Arrays.asList(new MachineOption(10L, 4L),
            new MachineOption(20L, 6L)));
    OperationSpec second = new OperationSpec(
        1L, 102L, 1, 200L, 30L, 1,
        Arrays.asList(new MachineOption(10L, 3L),
            new MachineOption(20L, 4L)));
    return new SchedulingProblem(
        Collections.singletonList(job),
        Arrays.asList(firstMachine, secondMachine),
        Collections.singletonList(resource),
        Arrays.asList(first, second),
        Arrays.asList(
            new SetupTimeSpec(10L, 100L, 100L, 0L),
            new SetupTimeSpec(10L, 100L, 200L, 2L),
            new SetupTimeSpec(10L, 200L, 100L, 1L),
            new SetupTimeSpec(10L, 200L, 200L, 0L),
            new SetupTimeSpec(20L, 100L, 100L, 0L),
            new SetupTimeSpec(20L, 100L, 200L, 2L),
            new SetupTimeSpec(20L, 200L, 100L, 1L),
            new SetupTimeSpec(20L, 200L, 200L, 0L)),
        Arrays.asList(
            new TransportTimeSpec(10L, 10L, 0L),
            new TransportTimeSpec(10L, 20L, 2L),
            new TransportTimeSpec(20L, 10L, 3L),
            new TransportTimeSpec(20L, 20L, 0L)),
        Arrays.asList(
            new ExternalEvent(
                0L, ExternalEventType.MACHINE_DELAY, 20L, 5L),
            new ExternalEvent(
                2L, ExternalEventType.JOB_RELEASE, 1L, 0L),
            new ExternalEvent(
                3L, ExternalEventType.MATERIAL_READY, 1L, 0L)));
  }

  public static void verifyInvalidInputRejected() {
    boolean rejected = false;
    try {
      new SchedulingProblem(
          Arrays.asList(
              new JobSpec(1L, 0L, 0L, 10L, 1, 1),
              new JobSpec(1L, 0L, 0L, 10L, 1, 1)),
          Collections.singletonList(new MachineSpec(10L, 100L, 0L,
              Collections.<MaintenanceInterval>emptyList())),
          Collections.singletonList(new ResourceSpec(30L, 1)),
          Collections.singletonList(new OperationSpec(
              1L, 101L, 0, 100L, 30L, 1,
              Collections.singletonList(new MachineOption(10L, 1L)))),
          Collections.singletonList(
              new SetupTimeSpec(10L, 100L, 100L, 0L)),
          Collections.singletonList(
              new TransportTimeSpec(10L, 10L, 0L)),
          Arrays.asList(
              new ExternalEvent(
                  0L, ExternalEventType.JOB_RELEASE, 1L, 0L),
              new ExternalEvent(
                  0L, ExternalEventType.MATERIAL_READY, 1L, 0L)));
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected) {
      throw new IllegalStateException("invalid detached input was accepted");
    }
  }

  public static void verifySemanticIdentity() {
    SchedulingProblem source = tinyOracle();
    ArrayList<JobSpec> jobs =
        reversed(source.jobs());
    ArrayList<MachineSpec> machines =
        reversed(source.machines());
    ArrayList<ResourceSpec> resources =
        reversed(source.resources());
    ArrayList<OperationSpec> operations =
        new ArrayList<OperationSpec>();
    for (OperationSpec operation : source.operations()) {
      operations.add(new OperationSpec(
          operation.jobId, operation.operationId, operation.sequence,
          operation.setupFamily, operation.resourceId,
          operation.resourceUnits, reversed(operation.options)));
    }
    Collections.reverse(operations);
    SchedulingProblem reordered = new SchedulingProblem(
        jobs, machines, resources, operations,
        reversed(source.setupTimes()),
        reversed(source.transportTimes()),
        reversed(source.events()));
    if (!source.checksum().equals(reordered.checksum())) {
      throw new IllegalStateException(
          "semantic input identity depends on collection order");
    }
    String sourceResult = new SomaSchedulingSolver()
        .solve(source).resultChecksum;
    String reorderedResult = new SomaSchedulingSolver()
        .solve(reordered).resultChecksum;
    if (!sourceResult.equals(reorderedResult)) {
      throw new IllegalStateException(
          "semantic input reordering changed the schedule");
    }
  }

  public static void verifyTimeOverflowRejected() {
    SchedulingProblem source = tinyOracle();
    ArrayList<ExternalEvent> events =
        new ArrayList<ExternalEvent>(source.events());
    events.add(new ExternalEvent(
        0L, ExternalEventType.MACHINE_DELAY, 10L, Long.MAX_VALUE));
    boolean rejected = false;
    try {
      new SchedulingProblem(
          source.jobs(), source.machines(), source.resources(),
          source.operations(), source.setupTimes(),
          source.transportTimes(), events);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected) {
      throw new IllegalStateException(
          "overflowing time horizon was accepted");
    }
  }

  public static void verifyResultClaimsClosed() {
    final SchedulingProblem problem = tinyOracle();
    SchedulingSolver solver = new SomaSchedulingSolver();
    final ScheduleResult result = solver.solve(problem);
    ScheduleValidator.validate(problem, result);
    expectInvalidResult(problem, new ScheduleResult(
        result.assignments(), result.completedJobs + 1,
        result.makespanMinute, result.totalTardinessMinutes,
        result.weightedTardiness, result.resultChecksum));
    expectInvalidResult(problem, new ScheduleResult(
        result.assignments(), result.completedJobs,
        result.makespanMinute + 1L, result.totalTardinessMinutes,
        result.weightedTardiness, result.resultChecksum));
    expectInvalidResult(problem, new ScheduleResult(
        result.assignments(), result.completedJobs,
        result.makespanMinute, result.totalTardinessMinutes + 1L,
        result.weightedTardiness, result.resultChecksum));
    expectInvalidResult(problem, new ScheduleResult(
        result.assignments(), result.completedJobs,
        result.makespanMinute, result.totalTardinessMinutes,
        result.weightedTardiness + 1L, result.resultChecksum));
    expectInvalidResult(problem, new ScheduleResult(
        result.assignments(), result.completedJobs,
        result.makespanMinute, result.totalTardinessMinutes,
        result.weightedTardiness, result.resultChecksum + "forged"));
  }

  private static void expectInvalidResult(
      SchedulingProblem problem, ScheduleResult result) {
    boolean rejected = false;
    try {
      ScheduleValidator.validate(problem, result);
    } catch (IllegalStateException expected) {
      rejected = true;
    }
    if (!rejected) {
      throw new IllegalStateException(
          "forged result claim was accepted");
    }
  }

  private static <T> ArrayList<T> reversed(List<T> values) {
    ArrayList<T> result = new ArrayList<T>(values);
    Collections.reverse(result);
    return result;
  }
}
