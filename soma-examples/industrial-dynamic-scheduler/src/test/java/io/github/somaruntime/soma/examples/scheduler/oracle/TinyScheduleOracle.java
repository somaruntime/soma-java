package io.github.somaruntime.soma.examples.scheduler.oracle;

import io.github.somaruntime.soma.examples.scheduler.problem.SchedulingProblem;
import io.github.somaruntime.soma.examples.scheduler.fixture.SchedulingProblemFixtures;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduledOperation;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleResult;
import io.github.somaruntime.soma.examples.scheduler.result.ScheduleValidator;
import io.github.somaruntime.soma.examples.scheduler.solver.SchedulingSolver;
import io.github.somaruntime.soma.examples.scheduler.solver.SomaSchedulingSolver;

import java.util.HashMap;
import java.util.Map;

/** 对两工序 fixture 使用手算结果，不用 SOMA 输出自证。 */
public final class TinyScheduleOracle {
  private TinyScheduleOracle() {
  }

  public static String verify() {
    SchedulingProblem problem = SchedulingProblemFixtures.tinyOracle();
    SchedulingSolver solver = new SomaSchedulingSolver();
    ScheduleResult result = solver.solve(problem);
    ScheduleValidator.validate(problem, result);
    Map<Long, ScheduledOperation> byOperation =
        new HashMap<Long, ScheduledOperation>();
    for (ScheduledOperation assignment : result.assignments()) {
      byOperation.put(Long.valueOf(assignment.operationId), assignment);
    }
    ScheduledOperation first = byOperation.get(Long.valueOf(101L));
    ScheduledOperation second = byOperation.get(Long.valueOf(102L));
    require(first != null && first.machineId == 10L,
          "first machine");
    require(first.setupStartMinute == 3L && first.startMinute == 3L
            && first.endMinute == 7L,
          "first timing");
    require(second != null && second.machineId == 20L,
          "second machine");
    require(second.transportMinutes == 2L && second.setupStartMinute == 9L
            && second.startMinute == 9L && second.endMinute == 13L,
          "second transport/timing");
    require(result.makespanMinute == 13L
            && result.totalTardinessMinutes == 1L
            && result.weightedTardiness == 2L,
          "oracle result");
    return result.resultChecksum;
  }

  private static void require(boolean condition, String label) {
    if (!condition) {
      throw new IllegalStateException("tiny schedule oracle failed: " + label);
    }
  }
}
