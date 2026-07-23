package com.hgtech.soma.examples.grassing.model;

import com.hgtech.soma.examples.grassing.support.StableHash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 通过校验、与 SOMA runtime 完全分离的不可变初始状态。 */
public final class SimulationInitialState {
  public static final int MODE_GRASSING = 0;
  public static final int MODE_SEARCHING = 1;

  private final int width;
  private final int height;
  private final double[] grass;
  private final List<IndividualInput> individuals;
  private final String checksum;

  public SimulationInitialState(int width, int height, double[] grass,
                                List<IndividualInput> individuals) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("world dimensions must be positive");
    }
    int cells = Math.multiplyExact(width, height);
    if (grass == null || grass.length != cells) {
      throw new IllegalArgumentException("grass length does not match world");
    }
    if (individuals == null) throw new NullPointerException("individuals");
    this.width = width;
    this.height = height;
    this.grass = Arrays.copyOf(grass, grass.length);
    ArrayList<IndividualInput> detached =
        new ArrayList<IndividualInput>(individuals.size());
    Set<Long> identities = new HashSet<Long>(
        Math.max(1, individuals.size() * 4 / 3 + 1));
    for (double value : this.grass) {
      requireFinite(value, "grass");
      if (value < 0.0) {
        throw new IllegalArgumentException("grass must be non-negative");
      }
    }
    for (IndividualInput individual : individuals) {
      if (individual == null) throw new NullPointerException("individual");
      individual.validate(width, height);
      if (!identities.add(Long.valueOf(individual.id))) {
        throw new IllegalArgumentException(
            "duplicate grasser identity: " + individual.id);
      }
      detached.add(individual);
    }
    this.individuals = Collections.unmodifiableList(detached);
    this.checksum = computeChecksum();
  }

  public int width() { return width; }
  public int height() { return height; }
  public int cellCount() { return grass.length; }
  public int population() { return individuals.size(); }
  public double[] grassCopy() { return Arrays.copyOf(grass, grass.length); }
  public List<IndividualInput> individuals() { return individuals; }
  public String checksum() { return checksum; }

  public SimulationInitialState withReversedIndividuals() {
    ArrayList<IndividualInput> reversed =
        new ArrayList<IndividualInput>(individuals);
    Collections.reverse(reversed);
    return new SimulationInitialState(width, height, grass, reversed);
  }

  private String computeChecksum() {
    StableHash hash = new StableHash()
        .addString("grassing-initial-state-v1")
        .addInt(width).addInt(height).addInt(grass.length);
    for (double value : grass) hash.addDouble(value);
    ArrayList<IndividualInput> canonical =
        new ArrayList<IndividualInput>(individuals);
    Collections.sort(canonical, new Comparator<IndividualInput>() {
      @Override
      public int compare(IndividualInput left, IndividualInput right) {
        return Long.compare(left.id, right.id);
      }
    });
    hash.addInt(canonical.size());
    for (IndividualInput individual : canonical) {
      hash.addLong(individual.id)
          .addInt(individual.x).addInt(individual.y)
          .addDouble(individual.energy)
          .addInt(individual.mode)
          .addInt(individual.movementDirection);
    }
    return hash.finishHex();
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  /** 一个 detached、stable-keyed 个体输入。 */
  public static final class IndividualInput {
    public final long id;
    public final int x;
    public final int y;
    public final double energy;
    public final int mode;
    public final int movementDirection;

    public IndividualInput(long id, int x, int y, double energy,
                           int mode, int movementDirection) {
      this.id = id;
      this.x = x;
      this.y = y;
      this.energy = energy;
      this.mode = mode;
      this.movementDirection = movementDirection;
    }

    void validate(int width, int height) {
      if (id <= 0L) throw new IllegalArgumentException("id must be positive");
      if (x < 0 || x >= width || y < 0 || y >= height) {
        throw new IllegalArgumentException("individual position out of bounds");
      }
      requireFinite(energy, "energy");
      if (energy <= 0.0) {
        throw new IllegalArgumentException("initial energy must be positive");
      }
      if (mode != MODE_GRASSING && mode != MODE_SEARCHING) {
        throw new IllegalArgumentException("unknown behaviour mode");
      }
      if (movementDirection < 0 || movementDirection >= 4) {
        throw new IllegalArgumentException("movement direction out of range");
      }
    }
  }
}
