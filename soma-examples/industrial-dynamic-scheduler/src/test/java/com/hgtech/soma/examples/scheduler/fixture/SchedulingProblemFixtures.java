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

import java.util.Arrays;
import java.util.Collections;

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
    return new SchedulingProblem(1, 77L,
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
      new SchedulingProblem(1, 1L,
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
}
