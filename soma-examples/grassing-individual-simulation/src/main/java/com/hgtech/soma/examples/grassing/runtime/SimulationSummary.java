package com.hgtech.soma.examples.grassing.runtime;

/** Runtime 的一次轻量聚合观察。 */
final class SimulationSummary {
  final double totalGrass;
  final double totalEnergy;
  final int grassing;
  final int searching;

  SimulationSummary(double totalGrass, double totalEnergy,
                    int grassing, int searching) {
    this.totalGrass = totalGrass;
    this.totalEnergy = totalEnergy;
    this.grassing = grassing;
    this.searching = searching;
  }
}
