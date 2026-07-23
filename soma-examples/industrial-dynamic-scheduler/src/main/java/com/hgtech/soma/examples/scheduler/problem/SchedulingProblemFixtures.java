package com.hgtech.soma.examples.scheduler.problem;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ExternalEvent;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.JobInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MachineOption;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.MaintenanceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.OperationInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.ResourceInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.SetupInput;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem.TransportInput;

import java.util.Arrays;
import java.util.Collections;

/** 手算 oracle 与输入失败路径；不进入 production generator。 */
public final class SchedulingProblemFixtures {
  private SchedulingProblemFixtures() {
  }

  public static SchedulingProblem tinyOracle() {
    JobInput job = new JobInput(1L, 2L, 3L, 12L, 2, 2);
    MachineInput firstMachine = new MachineInput(10L, 100L, 0L,
        Collections.singletonList(new MaintenanceInput(8L, 12L)));
    MachineInput secondMachine = new MachineInput(20L, 200L, 0L,
        Collections.<MaintenanceInput>emptyList());
    ResourceInput resource = new ResourceInput(30L, 1);
    OperationInput first = new OperationInput(1L, 101L, 0, 100L, 30L, 1,
        Arrays.asList(new MachineOption(10L, 4L),
            new MachineOption(20L, 6L)));
    OperationInput second = new OperationInput(1L, 102L, 1, 200L, 30L, 1,
        Arrays.asList(new MachineOption(10L, 3L),
            new MachineOption(20L, 4L)));
    return new SchedulingProblem(1, 77L,
        Collections.singletonList(job),
        Arrays.asList(firstMachine, secondMachine),
        Collections.singletonList(resource),
        Arrays.asList(first, second),
        Arrays.asList(
            new SetupInput(10L, 100L, 100L, 0L),
            new SetupInput(10L, 100L, 200L, 2L),
            new SetupInput(10L, 200L, 100L, 1L),
            new SetupInput(10L, 200L, 200L, 0L),
            new SetupInput(20L, 100L, 100L, 0L),
            new SetupInput(20L, 100L, 200L, 2L),
            new SetupInput(20L, 200L, 100L, 1L),
            new SetupInput(20L, 200L, 200L, 0L)),
        Arrays.asList(
            new TransportInput(10L, 10L, 0L),
            new TransportInput(10L, 20L, 2L),
            new TransportInput(20L, 10L, 3L),
            new TransportInput(20L, 20L, 0L)),
        Arrays.asList(
            new ExternalEvent(0L, SchedulingProblem.MACHINE_DELAY, 20L, 5L),
            new ExternalEvent(2L, SchedulingProblem.JOB_RELEASE, 1L, 0L),
            new ExternalEvent(3L, SchedulingProblem.MATERIAL_READY, 1L, 0L)));
  }

  public static void verifyInvalidInputRejected() {
    boolean rejected = false;
    try {
      new SchedulingProblem(1, 1L,
          Arrays.asList(
              new JobInput(1L, 0L, 0L, 10L, 1, 1),
              new JobInput(1L, 0L, 0L, 10L, 1, 1)),
          Collections.singletonList(new MachineInput(10L, 100L, 0L,
              Collections.<MaintenanceInput>emptyList())),
          Collections.singletonList(new ResourceInput(30L, 1)),
          Collections.singletonList(new OperationInput(
              1L, 101L, 0, 100L, 30L, 1,
              Collections.singletonList(new MachineOption(10L, 1L)))),
          Collections.singletonList(
              new SetupInput(10L, 100L, 100L, 0L)),
          Collections.singletonList(
              new TransportInput(10L, 10L, 0L)),
          Arrays.asList(
              new ExternalEvent(0L, SchedulingProblem.JOB_RELEASE, 1L, 0L),
              new ExternalEvent(0L, SchedulingProblem.MATERIAL_READY, 1L, 0L)));
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected) {
      throw new IllegalStateException("invalid detached input was accepted");
    }
  }
}
