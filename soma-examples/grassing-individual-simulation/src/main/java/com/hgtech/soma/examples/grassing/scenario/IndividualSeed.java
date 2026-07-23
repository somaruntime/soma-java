package com.hgtech.soma.examples.grassing.scenario;

/** 一个 detached、stable-keyed 的初始个体。 */
public final class IndividualSeed {
  public static final int MODE_GRASSING = 0;
  public static final int MODE_SEARCHING = 1;

  private final long id;
  private final int x;
  private final int y;
  private final double energy;
  private final int mode;
  private final int movementDirection;

  public IndividualSeed(long id, int x, int y, double energy,
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
    if (!Double.isFinite(energy) || energy <= 0.0) {
      throw new IllegalArgumentException(
          "initial energy must be positive and finite");
    }
    if (mode != MODE_GRASSING && mode != MODE_SEARCHING) {
      throw new IllegalArgumentException("unknown behaviour mode");
    }
    if (movementDirection < 0 || movementDirection >= 4) {
      throw new IllegalArgumentException("movement direction out of range");
    }
  }

  public long id() { return id; }
  public int x() { return x; }
  public int y() { return y; }
  public double energy() { return energy; }
  public int mode() { return mode; }
  public int movementDirection() { return movementDirection; }
}
