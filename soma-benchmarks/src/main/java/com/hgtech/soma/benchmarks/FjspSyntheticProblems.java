package com.hgtech.soma.benchmarks;

import com.hgtech.soma.examples.fjsp.FjspProblem;

/** Benchmark-only synthetic input generator；不拥有求解或 SOMA runtime policy。 */
final class FjspSyntheticProblems {
  private static final int JOBS = 1000;
  private static final int OPERATIONS_PER_JOB = 100;
  private static final int MACHINES = 100;
  private static final int CANDIDATES = 3;
  private static final long SETUP_FAMILY = 1L;

  private FjspSyntheticProblems() {
  }

  static FjspProblem oneHundredThousandOperations(long seed) {
    FjspProblem.Builder problem = FjspProblem.builder();
    for (int job = 0; job < JOBS; job++) {
      problem.addJob(job + 1L, job, OPERATIONS_PER_JOB * 100L,
        OPERATIONS_PER_JOB);
    }
    for (int machine = 0; machine < MACHINES; machine++) {
      long machineId = machine + 1L;
      problem.addMachine(machineId);
      problem.addSetupTime(
        machineId, SETUP_FAMILY, SETUP_FAMILY, 0L);
    }
    for (int job = 0; job < JOBS; job++) {
      for (int sequence = 0; sequence < OPERATIONS_PER_JOB; sequence++) {
        long[] machineIds = new long[CANDIDATES];
        long[] durations = new long[CANDIDATES];
        int firstMachine = positiveMod(mix(seed, job, sequence, 0), MACHINES);
        for (int candidate = 0; candidate < CANDIDATES; candidate++) {
          machineIds[candidate] = (firstMachine + candidate) % MACHINES + 1L;
          durations[candidate] = 1L + positiveMod(
            mix(seed, job, sequence, candidate + 1), 100);
        }
        long operationId = (long) job * OPERATIONS_PER_JOB + sequence + 1L;
        problem.addOperation(job + 1L, operationId, sequence, job,
          SETUP_FAMILY, job, 0L, machineIds, durations);
      }
    }
    return problem.build();
  }

  private static long mix(
      long seed, int job, int sequence, int candidate) {
    long value = seed ^ ((long) job * 0x9e3779b97f4a7c15L);
    value ^= (long) sequence * 0xc2b2ae3d27d4eb4fL;
    value ^= (long) candidate * 0x165667b19e3779f9L;
    value ^= value >>> 33;
    value *= 0xff51afd7ed558ccdL;
    value ^= value >>> 33;
    return value;
  }

  private static int positiveMod(long value, int divisor) {
    return (int) Math.floorMod(value, (long) divisor);
  }
}
