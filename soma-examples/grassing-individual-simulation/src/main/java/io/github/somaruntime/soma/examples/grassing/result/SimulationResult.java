package io.github.somaruntime.soma.examples.grassing.result;

/** 完成或观察一次 Session 时得到的不可变 detached 摘要。 */
public final class SimulationResult {
  private final int ticks;
  private final int population;
  private final int maximumPopulation;
  private final int grassingPopulation;
  private final int searchingPopulation;
  private final long births;
  private final long deaths;
  private final double totalGrass;
  private final double totalEnergy;
  private final String configChecksum;
  private final String inputChecksum;
  private final String resultChecksum;
  private final SimulationDiagnostics diagnostics;

  public SimulationResult(
      int ticks, int population, int maximumPopulation,
      int grassingPopulation, int searchingPopulation,
      long births, long deaths,
      double totalGrass, double totalEnergy,
      String configChecksum, String inputChecksum, String resultChecksum,
      SimulationDiagnostics diagnostics) {
    if (configChecksum == null) {
      throw new NullPointerException("configChecksum");
    }
    if (inputChecksum == null) throw new NullPointerException("inputChecksum");
    if (resultChecksum == null) {
      throw new NullPointerException("resultChecksum");
    }
    if (diagnostics == null) throw new NullPointerException("diagnostics");
    if (ticks < 0 || population < 0 || maximumPopulation < population
        || grassingPopulation < 0 || searchingPopulation < 0
        || grassingPopulation + searchingPopulation != population
        || births < 0L || deaths < 0L) {
      throw new IllegalArgumentException("invalid simulation summary");
    }
    if (!Double.isFinite(totalGrass) || !Double.isFinite(totalEnergy)) {
      throw new IllegalArgumentException(
          "simulation aggregates must be finite");
    }
    this.ticks = ticks;
    this.population = population;
    this.maximumPopulation = maximumPopulation;
    this.grassingPopulation = grassingPopulation;
    this.searchingPopulation = searchingPopulation;
    this.births = births;
    this.deaths = deaths;
    this.totalGrass = totalGrass;
    this.totalEnergy = totalEnergy;
    this.configChecksum = configChecksum;
    this.inputChecksum = inputChecksum;
    this.resultChecksum = resultChecksum;
    this.diagnostics = diagnostics;
  }

  public int ticks() { return ticks; }
  public int population() { return population; }
  public int maximumPopulation() { return maximumPopulation; }
  public int grassingPopulation() { return grassingPopulation; }
  public int searchingPopulation() { return searchingPopulation; }
  public long births() { return births; }
  public long deaths() { return deaths; }
  public double totalGrass() { return totalGrass; }
  public double totalEnergy() { return totalEnergy; }
  public String configChecksum() { return configChecksum; }
  public String inputChecksum() { return inputChecksum; }
  public String resultChecksum() { return resultChecksum; }
  public SimulationDiagnostics diagnostics() { return diagnostics; }
}
