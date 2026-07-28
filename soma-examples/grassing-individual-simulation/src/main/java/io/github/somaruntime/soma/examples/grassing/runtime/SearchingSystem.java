package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.config.SimulationConfig;
import io.github.somaruntime.soma.examples.grassing.schema.BehaviourMode;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateScan;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateUpdateCursor;
import io.github.somaruntime.soma.examples.grassing.support.DeterministicRandom;

/** 执行 searching movement 和 mode transition。 */
final class SearchingSystem {
  private static final int MOVEMENT_PROCESS = 22;

  private final SimulationRuntime runtime;
  private final SimulationConfig config;
  private long activeTick;

  SearchingSystem(SimulationRuntime runtime) {
    this.runtime = runtime;
    config = runtime.config;
  }

  void execute(long tick) {
    activeTick = tick;
    runtime.grassers.scanByMode(BehaviourMode.SEARCHING)
        .update(new GrasserStateScan.Updater() {
          @Override
          public void update(GrasserStateUpdateCursor candidate) {
            int direction = DeterministicRandom.bounded(
                config.seed(), activeTick, candidate.grasserIdValue(),
                MOVEMENT_PROCESS, 0, 4);
            int x = candidate.x();
            int y = candidate.y();
            if (direction == 0) {
              x = (x + 1) % config.width();
            } else if (direction == 1) {
              x = (x + config.width() - 1) % config.width();
            } else if (direction == 2) {
              y = (y + 1) % config.height();
            } else {
              y = (y + config.height() - 1) % config.height();
            }
            candidate.setX(x);
            candidate.setY(y);
            candidate.setMovementDirection(direction);
            double grass = runtime.grass[runtime.cell(x, y)];
            if (candidate.energy() <= config.searchEnergyThreshold()
                || grass >= config.grassingAmount() * 0.5) {
              candidate.setMode(BehaviourMode.GRASSING);
            }
          }
        });
  }
}
