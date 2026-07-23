package com.hgtech.soma.examples.grassing.validation;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.support.DeterministicRandom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** tiny correctness profile 使用的独立 AoS 参考实现。 */
final class ReferenceSimulation {
  private static final int REPRODUCTION_PROCESS = 20;
  private static final int CHILD_DIRECTION_PROCESS = 21;
  private static final int MOVEMENT_PROCESS = 22;

  private final SimulationConfig config;
  private final double[] grass;
  private final ArrayList<Individual> individuals;
  private final int[] cellPopulation;
  private long nextId;
  private long tick;
  private long births;
  private long deaths;

  ReferenceSimulation(
      SimulationConfig config, SimulationScenario scenario) {
    this.config = config;
    this.grass = scenario.grassCopy();
    this.individuals =
        new ArrayList<Individual>(scenario.population());
    long maximumId = 0L;
    for (IndividualSeed input : scenario.individuals()) {
      individuals.add(new Individual(
          input.id(), input.x(), input.y(), input.energy(),
          input.mode(), input.movementDirection()));
      maximumId = Math.max(maximumId, input.id());
    }
    this.nextId = Math.addExact(maximumId, 1L);
    this.cellPopulation = new int[grass.length];
  }

  void step() {
    tick++;
    growGrass();
    metabolizeAndRemoveDead();
    reproduce();
    grass();
    search();
  }

  private void growGrass() {
    double capacity = config.grassCarryingCapacity();
    double rate = config.grassGrowthRate();
    for (int cell = 0; cell < grass.length; cell++) {
      double value = grass[cell];
      double grown = value + rate * value * (1.0 - value / capacity);
      grass[cell] = Math.min(capacity, Math.max(0.0, grown));
    }
  }

  private void metabolizeAndRemoveDead() {
    Iterator<Individual> iterator = individuals.iterator();
    while (iterator.hasNext()) {
      Individual individual = iterator.next();
      individual.energy -= config.metabolismCost();
      if (individual.energy <= 0.0) {
        iterator.remove();
        deaths++;
      }
    }
  }

  private void reproduce() {
    ArrayList<Individual> parents = new ArrayList<Individual>();
    for (Individual individual : individuals) {
      if (individual.energy >= config.reproductionThreshold()
          && DeterministicRandom.unit(
              config.seed(), tick, individual.id,
              REPRODUCTION_PROCESS, 0)
          < config.reproductionProbability()) {
        parents.add(individual);
      }
    }
    Collections.sort(parents, new Comparator<Individual>() {
      @Override
      public int compare(Individual left, Individual right) {
        return Long.compare(left.id, right.id);
      }
    });
    if (parents.isEmpty()) return;
    ArrayList<Individual> offspring =
        new ArrayList<Individual>(parents.size());
    for (Individual parent : parents) {
      double childEnergy = parent.energy * 0.5;
      parent.energy *= 0.5;
      long childId = nextId++;
      int direction = DeterministicRandom.bounded(
          config.seed(), tick, childId, CHILD_DIRECTION_PROCESS, 0, 4);
      offspring.add(new Individual(
          childId, parent.x, parent.y, childEnergy,
          IndividualSeed.MODE_GRASSING, direction));
    }
    individuals.addAll(offspring);
    births += offspring.size();
  }

  private void grass() {
    Arrays.fill(cellPopulation, 0);
    for (Individual individual : individuals) {
      if (individual.mode == IndividualSeed.MODE_GRASSING) {
        cellPopulation[cell(individual.x, individual.y)]++;
      }
    }
    for (int cell = 0; cell < grass.length; cell++) {
      int count = cellPopulation[cell];
      if (count == 0) continue;
      double consumable = Math.max(
          0.0, grass[cell] - config.grassRegrowthFloor());
      double share = Math.min(config.grassingAmount(), consumable / count);
      for (Individual individual : individuals) {
        if (individual.mode == IndividualSeed.MODE_GRASSING
            && cell(individual.x, individual.y) == cell) {
          individual.energy += share;
          if (share < config.grassingAmount() * 0.5) {
            individual.mode = IndividualSeed.MODE_SEARCHING;
          }
        }
      }
      double remaining = grass[cell] - share * count;
      grass[cell] = Math.max(config.grassRegrowthFloor(), remaining);
    }
  }

  private void search() {
    for (Individual individual : individuals) {
      if (individual.mode != IndividualSeed.MODE_SEARCHING) continue;
      int direction = DeterministicRandom.bounded(
          config.seed(), tick, individual.id, MOVEMENT_PROCESS, 0, 4);
      if (direction == 0) {
        individual.x = (individual.x + 1) % config.width();
      } else if (direction == 1) {
        individual.x =
            (individual.x + config.width() - 1) % config.width();
      } else if (direction == 2) {
        individual.y = (individual.y + 1) % config.height();
      } else {
        individual.y =
            (individual.y + config.height() - 1) % config.height();
      }
      individual.direction = direction;
      if (individual.energy <= config.searchEnergyThreshold()
          || grass[cell(individual.x, individual.y)]
          >= config.grassingAmount() * 0.5) {
        individual.mode = IndividualSeed.MODE_GRASSING;
      }
    }
  }

  private int cell(int x, int y) {
    return y * config.width() + x;
  }

  long tick() { return tick; }
  long births() { return births; }
  long deaths() { return deaths; }
  double[] grassCopy() { return Arrays.copyOf(grass, grass.length); }

  List<Individual> canonicalIndividuals() {
    ArrayList<Individual> canonical =
        new ArrayList<Individual>(individuals.size());
    for (Individual individual : individuals) {
      canonical.add(individual.copy());
    }
    Collections.sort(canonical, new Comparator<Individual>() {
      @Override
      public int compare(Individual left, Individual right) {
        return Long.compare(left.id, right.id);
      }
    });
    return canonical;
  }

  static final class Individual {
    final long id;
    int x;
    int y;
    double energy;
    int mode;
    int direction;

    Individual(long id, int x, int y, double energy,
               int mode, int direction) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.energy = energy;
      this.mode = mode;
      this.direction = direction;
    }

    Individual copy() {
      return new Individual(id, x, y, energy, mode, direction);
    }
  }
}
