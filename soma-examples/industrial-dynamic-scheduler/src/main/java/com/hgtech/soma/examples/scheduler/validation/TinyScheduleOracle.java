package com.hgtech.soma.examples.scheduler.validation;

import com.hgtech.soma.examples.scheduler.problem.SchedulingProblem;
import com.hgtech.soma.examples.scheduler.problem.SchedulingProblemFixtures;
import com.hgtech.soma.examples.scheduler.runtime.IndustrialScheduler;
import com.hgtech.soma.examples.scheduler.runtime.ScheduleResult;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntime;
import com.hgtech.soma.examples.scheduler.runtime.SchedulerRuntimeBootstrap;
import com.hgtech.soma.examples.scheduler.state.OperationAssignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 对两工序 fixture 使用手算结果，不用 SOMA 输出自证。 */
public final class TinyScheduleOracle {
  private TinyScheduleOracle() {
  }

  public static String verify() {
    SchedulingProblem problem = SchedulingProblemFixtures.tinyOracle();
    SchedulerRuntime runtime = SchedulerRuntimeBootstrap.load(problem);
    try {
      ScheduleResult result = new IndustrialScheduler(runtime).solve();
      List<OperationAssignment> assignments = runtime.exportAssignments();
      ScheduleValidator.validate(problem, assignments, result);
      Map<Long, OperationAssignment> byOperation =
          new HashMap<Long, OperationAssignment>();
      for (OperationAssignment assignment : assignments) {
        byOperation.put(Long.valueOf(
            assignment.operationKey.operationId.value), assignment);
      }
      OperationAssignment first = byOperation.get(Long.valueOf(101L));
      OperationAssignment second = byOperation.get(Long.valueOf(102L));
      require(first != null && first.machineId.value == 10L,
          "first machine");
      require(first.setupStartMinute == 3L && first.startMinute == 3L
              && first.endMinute == 7L,
          "first timing");
      require(second != null && second.machineId.value == 20L,
          "second machine");
      require(second.transportMinutes == 2L && second.setupStartMinute == 9L
              && second.startMinute == 9L && second.endMinute == 13L,
          "second transport/timing");
      require(result.makespanMinute == 13L
              && result.totalTardinessMinutes == 1L
              && result.weightedTardiness == 2L,
          "oracle result");
      return result.resultChecksum;
    } finally {
      runtime.close();
    }
  }

  private static void require(boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException("tiny schedule oracle failed: " + label);
    }
  }
}
