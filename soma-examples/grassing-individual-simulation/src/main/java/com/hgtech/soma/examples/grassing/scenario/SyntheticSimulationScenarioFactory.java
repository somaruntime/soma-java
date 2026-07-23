package com.hgtech.soma.examples.grassing.scenario;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.support.DeterministicRandom;

import java.util.ArrayList;

/** 按版本化规则确定性生成合成仿真场景。 */
public final class SyntheticSimulationScenarioFactory
    implements SimulationScenarioFactory {
  private static final int GRASS_PROCESS = 1;
  private static final int POSITION_PROCESS = 2;
  private static final int ENERGY_PROCESS = 3;
  private static final int MODE_PROCESS = 4;
  private static final int DIRECTION_PROCESS = 5;

  @Override
  public SimulationScenario create(SimulationConfig config) {
    if (config == null) throw new NullPointerException("config");
    double[] grass = new double[config.cellCount()];
    double grassRange = config.grassMaximum() - config.grassMinimum();
    for (int cell = 0; cell < grass.length; cell++) {
      grass[cell] = config.grassMinimum() + grassRange
          * DeterministicRandom.unit(
              config.seed(), -1L, cell, GRASS_PROCESS, 0);
    }
    ArrayList<IndividualSeed> individuals =
        new ArrayList<IndividualSeed>(config.initialPopulation());
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
          ? IndividualSeed.MODE_GRASSING
          : IndividualSeed.MODE_SEARCHING;
      int direction = DeterministicRandom.bounded(
          config.seed(), -1L, id, DIRECTION_PROCESS, 0, 4);
      individuals.add(new IndividualSeed(
          id, x, y, energy, mode, direction));
    }
    return new SimulationScenario(config, grass, individuals);
  }
}
