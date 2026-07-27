package com.hgtech.soma.examples.scheduler.solver;

import com.hgtech.soma.examples.scheduler.schema.generated.OperationAssignmentTable;
import com.hgtech.soma.runtime.IntColumnView;
import com.hgtech.soma.runtime.LongColumnView;

/**
 * 从 authoritative assignment Table 单遍推导一次 solve 的领域指标。
 *
 * <p>当前 Index 只在同步只读批次中消费；job grouping 使用 application-owned
 * primitive open addressing，不建立第二份 live domain state。</p>
 */
final class AssignmentSummarizer {
  Metrics summarize(
      OperationAssignmentTable assignments,
      int expectedAssignments,
      int expectedJobs,
      long expectedMakespan) {
    if (assignments == null) throw new NullPointerException("assignments");
    if (expectedAssignments < 0) {
      throw new IllegalArgumentException(
          "expectedAssignments must be non-negative");
    }
    if (expectedJobs < 0) {
      throw new IllegalArgumentException(
          "expectedJobs must be non-negative");
    }
    if (expectedMakespan < 0L) {
      throw new IllegalArgumentException(
          "expectedMakespan must be non-negative");
    }

    Accumulator accumulator =
        new Accumulator(expectedAssignments, expectedJobs);
    int size = assignments.size();
    try (LongColumnView jobIds =
             assignments.operationKeyJobIdValueColumn();
         LongColumnView endMinutes = assignments.endMinuteColumn();
         LongColumnView dueMinutes = assignments.dueMinuteColumn();
         IntColumnView priorities = assignments.priorityColumn()) {
      for (int index = 0; index < size; index++) {
        accumulator.accept(
            jobIds.getLong(index),
            endMinutes.getLong(index),
            dueMinutes.getLong(index),
            priorities.getInt(index));
      }
    }
    return accumulator.finish(expectedMakespan);
  }

  static final class Metrics {
    final int assignments;
    final long makespan;
    final long totalTardiness;
    final long weightedTardiness;

    private Metrics(
        int assignments,
        long makespan,
        long totalTardiness,
        long weightedTardiness) {
      this.assignments = assignments;
      this.makespan = makespan;
      this.totalTardiness = totalTardiness;
      this.weightedTardiness = weightedTardiness;
    }
  }

  private static final class Accumulator {
    private final int expectedAssignments;
    private final int expectedJobs;
    private final boolean[] occupied;
    private final long[] jobIds;
    private final long[] completions;
    private final long[] dueMinutes;
    private final int[] priorities;
    private final int mask;
    private int assignments;
    private int jobs;
    private long makespan;

    Accumulator(int expectedAssignments, int expectedJobs) {
      this.expectedAssignments = expectedAssignments;
      this.expectedJobs = expectedJobs;
      int capacity = groupingCapacity(expectedJobs);
      occupied = new boolean[capacity];
      jobIds = new long[capacity];
      completions = new long[capacity];
      dueMinutes = new long[capacity];
      priorities = new int[capacity];
      mask = capacity - 1;
    }

    void accept(
        long jobId,
        long endMinute,
        long dueMinute,
        int priority) {
      assignments = Math.addExact(assignments, 1);
      makespan = Math.max(makespan, endMinute);
      int slot = slot(jobId);
      if (!occupied[slot]) {
        occupied[slot] = true;
        jobIds[slot] = jobId;
        completions[slot] = endMinute;
        dueMinutes[slot] = dueMinute;
        priorities[slot] = priority;
        jobs = Math.addExact(jobs, 1);
        return;
      }
      if (dueMinutes[slot] != dueMinute
          || priorities[slot] != priority) {
        throw new IllegalStateException(
            "assignment facts disagree within job " + jobId);
      }
      completions[slot] = Math.max(completions[slot], endMinute);
    }

    Metrics finish(long expectedMakespan) {
      if (assignments != expectedAssignments) {
        throw new IllegalStateException(
            "assignment summary count does not cover the solve");
      }
      if (jobs != expectedJobs) {
        throw new IllegalStateException(
            "assignment summary jobs do not cover the solve");
      }
      if (makespan != expectedMakespan) {
        throw new IllegalStateException(
            "assignment summary makespan disagrees with dispatch state");
      }
      long total = 0L;
      long weighted = 0L;
      for (int slot = 0; slot < occupied.length; slot++) {
        if (!occupied[slot]) continue;
        long late = Math.max(
            0L,
            Math.subtractExact(
                completions[slot], dueMinutes[slot]));
        total = Math.addExact(total, late);
        weighted = Math.addExact(
            weighted,
            Math.multiplyExact(
                late, (long) priorities[slot]));
      }
      if (total < 0L || weighted < 0L) {
        throw new IllegalStateException(
            "assignment summary produced negative tardiness");
      }
      return new Metrics(
          assignments, makespan, total, weighted);
    }

    private int slot(long jobId) {
      int slot = mixed(jobId) & mask;
      while (occupied[slot] && jobIds[slot] != jobId) {
        slot = (slot + 1) & mask;
      }
      return slot;
    }

    private static int groupingCapacity(int expectedJobs) {
      long required = Math.max(
          2L, Math.multiplyExact((long) expectedJobs, 2L));
      if (required > (1L << 30)) {
        throw new IllegalArgumentException(
            "expectedJobs exceeds application range");
      }
      int capacity = 2;
      while (capacity < required) capacity <<= 1;
      return capacity;
    }

    private static int mixed(long value) {
      long mixed = value;
      mixed ^= mixed >>> 33;
      mixed *= 0xff51afd7ed558ccdL;
      mixed ^= mixed >>> 33;
      mixed *= 0xc4ceb9fe1a85ec53L;
      mixed ^= mixed >>> 33;
      return (int) mixed;
    }
  }
}
