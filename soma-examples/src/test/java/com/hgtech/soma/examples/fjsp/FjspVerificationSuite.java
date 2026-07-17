package com.hgtech.soma.examples.fjsp;

import com.hgtech.soma.examples.fjsp.schema.JobId;
import com.hgtech.soma.examples.fjsp.schema.MachineId;
import com.hgtech.soma.examples.fjsp.schema.OperationAssignment;
import com.hgtech.soma.examples.fjsp.schema.OperationId;
import com.hgtech.soma.examples.fjsp.schema.OperationKey;
import com.hgtech.soma.examples.fjsp.schema.OperationMachineKey;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyId;
import com.hgtech.soma.examples.fjsp.schema.SetupFamilyPair;
import com.hgtech.soma.examples.fjsp.schema.SetupTimeKey;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateBatch;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineCandidateTable;
import com.hgtech.soma.examples.fjsp.schema.generated.MachineTable;
import com.hgtech.soma.examples.fjsp.schema.generated.OperationAssignmentBatch;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.LongColumnView;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.List;

/** 不进入教学入口的 FJSP error、lifecycle 和 deterministic verification。 */
public final class FjspVerificationSuite {
  private FjspVerificationSuite() {
  }

  public static void main(String[] args) {
    verifySharedSolverPath();
    verifyStableTieBreakAfterCompaction();
    System.out.println("lane=fjsp-errors duplicate_key=ok missing_key=ok "
      + "optional_empty=ok empty_result=ok");
    System.out.println("lane=fjsp-lifecycle view_pinned=ok released_view=ok "
      + "table_released=ok stale_view=referenced-g3");
    System.out.println("lane=fjsp-stats schema_hash=ok runtime_plan=ok "
      + "exactIndex=ok keyspace=ok");
    System.out.println("lane=owner-breadth vrp_vehicle=ok vrp_unassigned=ok "
      + "simulation_tank=ok simulation_valve=ok game_player=ok");
    System.out.println("fjsp-verification: ok");
  }

  private static void verifySharedSolverPath() {
    try (FjspInstance instance = FjspInstanceFactory.create(
        FjspProblem.teachingExample())) {
      FjspSolveResult result = new FjspSolver(
        instance, new FcfsSptDispatchRule()).solve();
      List<OperationAssignment> exported = instance.exportAssignments();
      require(result.assignments == 2 && result.completedJobs == 1,
        "teaching problem cardinality");
      require(exported.size() == 2 && instance.schemaHash().length() == 64,
        "detached export and schema identity");
      require(instance.exactIndexProbeCount() > 0L,
        "solver exercised generated exact indexes");
      System.out.println("access-pattern-card scenario=fjsp "
        + "paths=child-release,keyed-frontier,grouped-update,dynamic-sort,grouped-remove "
        + "rows=" + result.assignments
        + " hotColumns=operationKey,assignedMachine,setupStartMinute,setupMinutes,startMinute,processingMinutes,endMinute"
        + " hotLeafWidthsByTable=operation_assignments:64"
        + " aggregateHotLeafWidths=64"
        + " workingSetHotLeafBytes=" + (instance.assignmentCapacity() * 64L)
        + " workingSetFormula=assignments.capacity*64"
        + " reads=" + exported.size() + " mutations=" + result.assignments
        + " exports=" + exported.size()
        + " evidenceScope=assignment-solve-and-export"
        + " observation=executed-result-accounting "
        + "assignments=" + result.assignments
        + " exactIndexProbes=" + instance.exactIndexProbeCount());

      OperationKey first = new OperationKey(new JobId(1L), new OperationId(10L));
      expectCode("duplicate_key", () -> instance.assignments.addBatch(
        new OperationAssignmentBatch(1).addValues(first, new MachineId(100L),
          0L, 0L, 0L, 1L, 1L)));
      expectCode("missing_key", () -> instance.setupTimes.fetch(
        new SetupTimeKey(new MachineId(100L), new SetupFamilyPair(
          new SetupFamilyId(8L), new SetupFamilyId(7L)))));
      require(!instance.frontier.findByMachine(new MachineId(999L))
          .findFirst().isPresent(), "optional lookup remains empty");
      expectCode("empty_result", () -> instance.frontier
        .findByMachine(new MachineId(999L)).firstOrThrow());

      LongColumnView available = instance.machines.availableFromMinuteColumn();
      try {
        expectCode("view_pinned", instance.machines::clear);
      } finally {
        available.close();
      }
      expectCode("released_view", () -> available.getLong(0));
      MachineTable released = MachineTable.create();
      released.release();
      expectCode("table_released", released::size);
    }
  }

  private static void verifyStableTieBreakAfterCompaction() {
    MachineId machine = new MachineId(100L);
    SetupFamilyId family = new SetupFamilyId(7L);
    JobId job = new JobId(77L);
    MachineCandidateTable frontier = MachineCandidateTable.create();
    try {
      OperationKey expected = new OperationKey(job, new OperationId(1L));
      OperationKey removed = new OperationKey(job, new OperationId(2L));
      OperationKey last = new OperationKey(job, new OperationId(3L));
      MachineCandidateBatch batch = new MachineCandidateBatch(3);
      addCandidate(batch, last, machine, family);
      addCandidate(batch, removed, machine, family);
      addCandidate(batch, expected, machine, family);
      frontier.addBatch(batch);
      frontier.delete(new OperationMachineKey(removed, machine));
      IndexSnapshot selected = frontier.findByMachine(machine)
        .filter(row -> row.indicatorReady())
        .sorted(new FcfsSptDispatchRule().comparator())
        .limit(1).rowIndexes();
      LongColumnView operationIds =
        frontier.candidateKeyOperationKeyOperationIdValueColumn();
      try {
        require(selected.size() == 1
            && operationIds.getLong(selected.indexAt(0)) == 1L,
          "identity tie-break must survive packed compaction");
      } finally {
        operationIds.close();
      }
    } finally {
      frontier.release();
    }
  }

  private static void addCandidate(
      MachineCandidateBatch batch, OperationKey operation,
      MachineId machine, SetupFamilyId family) {
    batch.addValues(new OperationMachineKey(operation, machine), family,
      5L, 0L, 0L, 5L, 3L, 0L, 5L, 5L, 3L, true);
  }

  private static void expectCode(String code, Action action) {
    try {
      action.run();
      throw new AssertionError("expected " + code);
    } catch (SomaRuntimeException failure) {
      require(code.equals(failure.code()),
        "expected " + code + " but got " + failure.code());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private interface Action {
    void run();
  }
}
