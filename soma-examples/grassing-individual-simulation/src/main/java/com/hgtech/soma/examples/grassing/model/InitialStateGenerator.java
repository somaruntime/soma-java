package com.hgtech.soma.examples.grassing.model;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.model.SimulationInitialState.IndividualInput;
import com.hgtech.soma.examples.grassing.support.DeterministicRandom;

import java.util.ArrayList;

/** 只从配置生成 detached input，不持有或调用 SOMA runtime。 */
public final class InitialStateGenerator {
  private static final int GRASS_PROCESS = 1;
  private static final int POSITION_PROCESS = 2;
  private static final int ENERGY_PROCESS = 3;
  private static final int MODE_PROCESS = 4;
  private static final int DIRECTION_PROCESS = 5;

  private InitialStateGenerator() {
  }

  public static SimulationInitialState generate(SimulationConfig config) {
    if (config == null) throw new NullPointerException("config");
    double[] grass = new double[config.cellCount()];
    double grassRange = config.grassMaximum() - config.grassMinimum();
    for (int cell = 0; cell < grass.length; cell++) {
      grass[cell] = config.grassMinimum() + grassRange
          * DeterministicRandom.unit(
              config.seed(), -1L, cell, GRASS_PROCESS, 0);
    }
    ArrayList<IndividualInput> individuals =
        new ArrayList<IndividualInput>(config.initialPopulation());
    double energyRange = config.energyMaximum() - config.energyMinimum();
    for (int index = 0; index < config.initialPopulation(); index++) {
      long id = index + 1L;
      int x = DeterministicRandom.bounded(
          config.seed(), -1L, id, POSITION_PROCESS, 0, config.width());
      int y = DeterministicRandom.bounded(
          config.seed(), -1L, id, POSITION_PROCESS, 1, config.height());
      double energy = config.energyMinimum() + energyRange
          * DeterministicRandom.unit(
              config.seed(), -1L, id, ENERGY_PROCESS, 0);
      int mode = DeterministicRandom.unit(
          config.seed(), -1L, id, MODE_PROCESS, 0) < 0.5
          ? SimulationInitialState.MODE_GRASSING
          : SimulationInitialState.MODE_SEARCHING;
      int direction = DeterministicRandom.bounded(
          config.seed(), -1L, id, DIRECTION_PROCESS, 0, 4);
      individuals.add(new IndividualInput(
          id, x, y, energy, mode, direction));
    }
    return new SimulationInitialState(
        config.width(), config.height(), grass, individuals);
  }
}
