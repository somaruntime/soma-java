package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;
import io.github.somaruntime.soma.examples.grassing.schema.BehaviourMode;
import io.github.somaruntime.soma.examples.grassing.schema.GrasserId;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateBatch;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateCursor;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateScan;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateUpdateCursor;
import io.github.somaruntime.soma.examples.grassing.support.DeterministicRandom;

import java.util.Arrays;

/** 按 stable ID 顺序执行 two-phase reproduction publish。 */
final class ReproductionSystem {
  private static final int REPRODUCTION_PROCESS = 20;
  private static final int CHILD_DIRECTION_PROCESS = 21;

  private final SimulationRuntime runtime;
  private final SimulationConfig config;
  private final GrasserStateBatch offspringBatch = new GrasserStateBatch(256);
  private int[] stagedX = new int[256];
  private int[] stagedY = new int[256];
  private double[] stagedEnergy = new double[256];
  private int stagedCount;
  private long activeTick;

  ReproductionSystem(SimulationRuntime runtime) {
    this.runtime = runtime;
    config = runtime.config;
  }

  int execute(long tick) {
    activeTick = tick;
    stagedCount = 0;
    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return reproductionCandidate(candidate);
      }
    }).sorted(new GrasserStateScan.Comparator() {
      @Override
      public int compare(GrasserStateCursor left, GrasserStateCursor right) {
        return Long.compare(left.grasserIdValue(), right.grasserIdValue());
      }
    }).forEach(new GrasserStateScan.Consumer() {
      @Override
      public void accept(GrasserStateCursor candidate) {
        stageParent(candidate);
      }
    });
    if (stagedCount == 0) return 0;

    offspringBatch.clear();
    long nextId = runtime.nextId();
    for (int index = 0; index < stagedCount; index++) {
      long childId = Math.addExact(nextId, index);
      int direction = DeterministicRandom.bounded(
          config.seed(), tick, childId, CHILD_DIRECTION_PROCESS, 0, 4);
      offspringBatch.addValues(new GrasserId(childId),
          stagedX[index], stagedY[index], stagedEnergy[index] * 0.5,
          BehaviourMode.GRASSING, direction);
    }

    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return reproductionCandidate(candidate);
      }
    }).update(new GrasserStateScan.Updater() {
      @Override
      public void update(GrasserStateUpdateCursor candidate) {
        candidate.setEnergy(candidate.energy() * 0.5);
      }
    });
    runtime.grassers.addBatch(offspringBatch);
    runtime.nextId(Math.addExact(nextId, stagedCount));
    return stagedCount;
  }

  private boolean reproductionCandidate(GrasserStateCursor candidate) {
    return candidate.energy() >= config.reproductionThreshold()
        && DeterministicRandom.unit(
            config.seed(), activeTick, candidate.grasserIdValue(),
            REPRODUCTION_PROCESS, 0) < config.reproductionProbability();
  }

  private void stageParent(GrasserStateCursor candidate) {
    ensureStaging(stagedCount + 1);
    stagedX[stagedCount] = candidate.x();
    stagedY[stagedCount] = candidate.y();
    stagedEnergy[stagedCount] = candidate.energy();
    stagedCount++;
  }

  private void ensureStaging(int required) {
    if (required <= stagedX.length) return;
    int next = Math.max(required, stagedX.length * 3 / 2 + 1);
    stagedX = Arrays.copyOf(stagedX, next);
    stagedY = Arrays.copyOf(stagedY, next);
    stagedEnergy = Arrays.copyOf(stagedEnergy, next);
  }
}
