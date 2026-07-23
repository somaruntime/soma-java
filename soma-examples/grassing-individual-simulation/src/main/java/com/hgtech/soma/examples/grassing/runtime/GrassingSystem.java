package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.schema.BehaviourMode;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateCursor;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateScan;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateUpdateCursor;

import java.util.Arrays;

/** 计算 cell share，并以 two-phase 方式发布摄食结果。 */
final class GrassingSystem {
  private final SimulationRuntime runtime;
  private final SimulationConfig config;

  GrassingSystem(SimulationRuntime runtime) {
    this.runtime = runtime;
    config = runtime.config;
  }

  void execute() {
    Arrays.fill(runtime.cellPopulation, 0);
    Arrays.fill(runtime.cellShare, 0.0);
    Arrays.fill(runtime.cellConsumption, 0.0);
    runtime.grassers.scanByMode(BehaviourMode.GRASSING)
        .forEach(new GrasserStateScan.Consumer() {
          @Override
          public void accept(GrasserStateCursor candidate) {
            int cell = runtime.cell(candidate.x(), candidate.y());
            runtime.cellPopulation[cell] =
                Math.addExact(runtime.cellPopulation[cell], 1);
          }
        });
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      int consumers = runtime.cellPopulation[cell];
      if (consumers == 0) continue;
      double consumable = Math.max(
          0.0, runtime.grass[cell] - config.grassRegrowthFloor());
      double share = Math.min(
          config.grassingAmount(), consumable / consumers);
      runtime.cellShare[cell] = share;
      runtime.cellConsumption[cell] = share * consumers;
      requireFinite(share, "grassing share");
    }
    runtime.grassers.scanByMode(BehaviourMode.GRASSING)
        .update(new GrasserStateScan.Updater() {
          @Override
          public void update(GrasserStateUpdateCursor candidate) {
            int cell = runtime.cell(candidate.x(), candidate.y());
            double share = runtime.cellShare[cell];
            candidate.setEnergy(candidate.energy() + share);
            if (share < config.grassingAmount() * 0.5) {
              candidate.setMode(BehaviourMode.SEARCHING);
            }
          }
        });
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      double remaining =
          runtime.grass[cell] - runtime.cellConsumption[cell];
      runtime.grass[cell] =
          Math.max(config.grassRegrowthFloor(), remaining);
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " is not finite");
    }
  }
}
