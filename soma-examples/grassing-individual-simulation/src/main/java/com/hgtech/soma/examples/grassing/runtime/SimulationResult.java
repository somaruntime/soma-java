package com.hgtech.soma.examples.grassing.runtime;

/** 完成一次 simulation session 后的稳定摘要。 */
public final class SimulationResult {
  public final int ticks;
  public final int population;
  public final int maximumPopulation;
  public final int grassingPopulation;
  public final int searchingPopulation;
  public final long births;
  public final long deaths;
  public final double totalGrass;
  public final double totalEnergy;
  public final String resultChecksum;

  SimulationResult(int ticks, int population, int maximumPopulation,
                   int grassingPopulation, int searchingPopulation,
                   long births, long deaths,
                   double totalGrass, double totalEnergy,
                   String resultChecksum) {
    this.ticks = ticks;
    this.population = population;
    this.maximumPopulation = maximumPopulation;
    this.grassingPopulation = grassingPopulation;
    this.searchingPopulation = searchingPopulation;
    this.births = births;
    this.deaths = deaths;
    this.totalGrass = totalGrass;
    this.totalEnergy = totalEnergy;
    this.resultChecksum = resultChecksum;
  }
}
