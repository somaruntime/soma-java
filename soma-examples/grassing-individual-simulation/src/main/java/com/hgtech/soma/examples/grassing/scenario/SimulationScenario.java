package com.hgtech.soma.examples.grassing.scenario;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.support.StableHash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 一次仿真的不可变、detached 输入。 */
public final class SimulationScenario {
  private final SimulationConfig config;
  private final double[] grass;
  private final List<IndividualSeed> individuals;
  private final String checksum;

  public SimulationScenario(SimulationConfig config, double[] grass,
                            List<IndividualSeed> individuals) {
    if (config == null) throw new NullPointerException("config");
    if (grass == null) throw new NullPointerException("grass");
    if (individuals == null) throw new NullPointerException("individuals");
    if (grass.length != config.cellCount()) {
      throw new IllegalArgumentException("grass length does not match world");
    }
    if (individuals.size() != config.initialPopulation()) {
      throw new IllegalArgumentException(
          "initial population differs from config");
    }
    this.config = config;
    this.grass = Arrays.copyOf(grass, grass.length);
    ArrayList<IndividualSeed> detached =
        new ArrayList<IndividualSeed>(individuals.size());
    Set<Long> identities = new HashSet<Long>(
        Math.max(1, individuals.size() * 4 / 3 + 1));
    for (double value : this.grass) {
      if (!Double.isFinite(value) || value < 0.0) {
        throw new IllegalArgumentException(
            "grass must be non-negative and finite");
      }
      if (value > config.grassCarryingCapacity()) {
        throw new IllegalArgumentException(
            "initial grass exceeds configured carrying capacity");
      }
    }
    for (IndividualSeed individual : individuals) {
      if (individual == null) throw new NullPointerException("individual");
      individual.validate(config.width(), config.height());
      if (!identities.add(Long.valueOf(individual.id()))) {
        throw new IllegalArgumentException(
            "duplicate grasser identity: " + individual.id());
      }
      detached.add(individual);
    }
    this.individuals = Collections.unmodifiableList(detached);
    checksum = computeChecksum();
  }

  public SimulationConfig config() { return config; }
  public int width() { return config.width(); }
  public int height() { return config.height(); }
  public int cellCount() { return grass.length; }
  public int population() { return individuals.size(); }
  public double[] grassCopy() { return Arrays.copyOf(grass, grass.length); }
  public List<IndividualSeed> individuals() { return individuals; }
  public String checksum() { return checksum; }

  private String computeChecksum() {
    StableHash hash = new StableHash()
        .addString("grassing-initial-state-v1")
        .addInt(width()).addInt(height()).addInt(grass.length);
    for (double value : grass) hash.addDouble(value);
    ArrayList<IndividualSeed> canonical =
        new ArrayList<IndividualSeed>(individuals);
    Collections.sort(canonical, new Comparator<IndividualSeed>() {
      @Override
      public int compare(IndividualSeed left, IndividualSeed right) {
        return Long.compare(left.id(), right.id());
      }
    });
    hash.addInt(canonical.size());
    for (IndividualSeed individual : canonical) {
      hash.addLong(individual.id())
          .addInt(individual.x()).addInt(individual.y())
          .addDouble(individual.energy())
          .addInt(individual.mode())
          .addInt(individual.movementDirection());
    }
    return hash.finishHex();
  }
}
