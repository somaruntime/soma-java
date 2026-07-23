package com.hgtech.soma.examples.grassing.config;

import com.hgtech.soma.examples.grassing.support.StableHash;

import java.util.TreeMap;

/** 经过验证、版本化且可完整输出生效值的不可变仿真配置。 */
public final class SimulationConfig {
  private final TreeMap<String, String> values;
  private final int configVersion;
  private final int generatorVersion;
  private final long seed;
  private final int width;
  private final int height;
  private final int initialPopulation;
  private final int ticks;
  private final double grassMinimum;
  private final double grassMaximum;
  private final double grassCarryingCapacity;
  private final double grassGrowthRate;
  private final double grassRegrowthFloor;
  private final double energyMinimum;
  private final double energyMaximum;
  private final double metabolismCost;
  private final double reproductionThreshold;
  private final double reproductionProbability;
  private final double grassingAmount;
  private final double searchEnergyThreshold;
  private final int traceInterval;
  private final int benchmarkWarmup;
  private final int benchmarkForks;
  private final int benchmarkMeasurements;

  SimulationConfig(TreeMap<String, String> values) {
    this.values = new TreeMap<String, String>(values);
    configVersion = parseInt("config.version");
    generatorVersion = parseInt("generator.version");
    seed = parseLong("seed");
    width = parseInt("world.width");
    height = parseInt("world.height");
    initialPopulation = parseInt("initial.population");
    ticks = parseInt("ticks");
    grassMinimum = parseDouble("initial.grass.minimum");
    grassMaximum = parseDouble("initial.grass.maximum");
    grassCarryingCapacity = parseDouble("grass.carrying.capacity");
    grassGrowthRate = parseDouble("grass.growth.rate");
    grassRegrowthFloor = parseDouble("grass.regrowth.floor");
    energyMinimum = parseDouble("initial.energy.minimum");
    energyMaximum = parseDouble("initial.energy.maximum");
    metabolismCost = parseDouble("metabolism.cost");
    reproductionThreshold = parseDouble("reproduction.threshold");
    reproductionProbability = parseDouble("reproduction.probability");
    grassingAmount = parseDouble("grassing.amount");
    searchEnergyThreshold = parseDouble("search.energy.threshold");
    traceInterval = parseInt("trace.interval");
    benchmarkWarmup = parseInt("benchmark.warmup");
    benchmarkForks = parseInt("benchmark.forks");
    benchmarkMeasurements = parseInt("benchmark.measurements");
    validate();
  }

  private void validate() {
    require(intValue("config.version") == 1, "unsupported config.version");
    require(intValue("generator.version") == 1, "unsupported generator.version");
    require(longValue("seed") >= 0L, "seed must be non-negative");
    requirePositive("world.width");
    requirePositive("world.height");
    requirePositive("initial.population");
    requireNonNegative("ticks");
    Math.multiplyExact(width(), height());
    require(cellCount() <= 16 * 1024 * 1024,
        "world contains too many cells for this reference application");
    require(initialPopulation() <= 10 * 1024 * 1024,
        "initial population exceeds reference application boundary");

    requireFinite("initial.grass.minimum");
    requireFinite("initial.grass.maximum");
    requireFinite("grass.carrying.capacity");
    require(grassMinimum() >= 0.0, "grass minimum must be non-negative");
    require(grassMaximum() >= grassMinimum(),
        "grass maximum is smaller than minimum");
    require(grassCarryingCapacity() > 0.0,
        "grass carrying capacity must be positive");
    require(grassMaximum() <= grassCarryingCapacity(),
        "initial grass exceeds carrying capacity");
    requireRange("grass.growth.rate", 0.0, 1.0);
    requireFinite("grass.regrowth.floor");
    require(grassRegrowthFloor() >= 0.0
            && grassRegrowthFloor() <= grassMinimum(),
        "grass regrowth floor must be in [0, initial minimum]");

    requireFinite("initial.energy.minimum");
    requireFinite("initial.energy.maximum");
    require(energyMinimum() > 0.0, "energy minimum must be positive");
    require(energyMaximum() >= energyMinimum(),
        "energy maximum is smaller than minimum");
    requirePositiveFinite("metabolism.cost");
    requirePositiveFinite("reproduction.threshold");
    requireRange("reproduction.probability", 0.0, 1.0);
    requirePositiveFinite("grassing.amount");
    requireFinite("search.energy.threshold");
    require(searchEnergyThreshold() >= 0.0,
        "search energy threshold must be non-negative");
    requirePositive("trace.interval");
    requireNonNegative("benchmark.warmup");
    requirePositive("benchmark.forks");
    requirePositive("benchmark.measurements");
  }

  private void requirePositive(String key) {
    require(intValue(key) > 0, key + " must be positive");
  }

  private void requireNonNegative(String key) {
    require(intValue(key) >= 0, key + " must be non-negative");
  }

  private void requirePositiveFinite(String key) {
    requireFinite(key);
    require(doubleValue(key) > 0.0, key + " must be positive");
  }

  private void requireRange(String key, double minimum, double maximum) {
    requireFinite(key);
    double value = doubleValue(key);
    require(value >= minimum && value <= maximum,
        key + " must be in [" + minimum + ", " + maximum + "]");
  }

  private void requireFinite(String key) {
    require(Double.isFinite(doubleValue(key)), key + " must be finite");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private int parseInt(String key) {
    try {
      return Integer.parseInt(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be an integer", invalid);
    }
  }

  private long parseLong(String key) {
    try {
      return Long.parseLong(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be a long", invalid);
    }
  }

  private double parseDouble(String key) {
    try {
      return Double.parseDouble(values.get(key));
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException(key + " must be a number", invalid);
    }
  }

  private int intValue(String key) {
    if ("config.version".equals(key)) return configVersion;
    if ("generator.version".equals(key)) return generatorVersion;
    if ("world.width".equals(key)) return width;
    if ("world.height".equals(key)) return height;
    if ("initial.population".equals(key)) return initialPopulation;
    if ("ticks".equals(key)) return ticks;
    if ("trace.interval".equals(key)) return traceInterval;
    if ("benchmark.warmup".equals(key)) return benchmarkWarmup;
    if ("benchmark.forks".equals(key)) return benchmarkForks;
    if ("benchmark.measurements".equals(key)) return benchmarkMeasurements;
    throw new IllegalArgumentException("not an integer config key: " + key);
  }

  private long longValue(String key) {
    if ("seed".equals(key)) return seed;
    throw new IllegalArgumentException("not a long config key: " + key);
  }

  private double doubleValue(String key) {
    if ("initial.grass.minimum".equals(key)) return grassMinimum;
    if ("initial.grass.maximum".equals(key)) return grassMaximum;
    if ("grass.carrying.capacity".equals(key)) return grassCarryingCapacity;
    if ("grass.growth.rate".equals(key)) return grassGrowthRate;
    if ("grass.regrowth.floor".equals(key)) return grassRegrowthFloor;
    if ("initial.energy.minimum".equals(key)) return energyMinimum;
    if ("initial.energy.maximum".equals(key)) return energyMaximum;
    if ("metabolism.cost".equals(key)) return metabolismCost;
    if ("reproduction.threshold".equals(key)) return reproductionThreshold;
    if ("reproduction.probability".equals(key)) {
      return reproductionProbability;
    }
    if ("grassing.amount".equals(key)) return grassingAmount;
    if ("search.energy.threshold".equals(key)) return searchEnergyThreshold;
    throw new IllegalArgumentException("not a numeric config key: " + key);
  }

  public int configVersion() { return configVersion; }
  public int generatorVersion() { return generatorVersion; }
  public long seed() { return seed; }
  public int width() { return width; }
  public int height() { return height; }
  public int cellCount() { return Math.multiplyExact(width(), height()); }
  public int initialPopulation() { return initialPopulation; }
  public int ticks() { return ticks; }
  public double grassMinimum() { return grassMinimum; }
  public double grassMaximum() { return grassMaximum; }
  public double grassCarryingCapacity() {
    return grassCarryingCapacity;
  }
  public double grassGrowthRate() { return grassGrowthRate; }
  public double grassRegrowthFloor() {
    return grassRegrowthFloor;
  }
  public double energyMinimum() { return energyMinimum; }
  public double energyMaximum() { return energyMaximum; }
  public double metabolismCost() { return metabolismCost; }
  public double reproductionThreshold() {
    return reproductionThreshold;
  }
  public double reproductionProbability() {
    return reproductionProbability;
  }
  public double grassingAmount() { return grassingAmount; }
  public double searchEnergyThreshold() {
    return searchEnergyThreshold;
  }
  public int traceInterval() { return traceInterval; }
  public int benchmarkWarmup() { return benchmarkWarmup; }
  public int benchmarkForks() { return benchmarkForks; }
  public int benchmarkMeasurements() {
    return benchmarkMeasurements;
  }

  public String canonicalText() {
    StringBuilder text = new StringBuilder();
    for (String key : values.keySet()) {
      text.append(key).append('=').append(values.get(key)).append('\n');
    }
    return text.toString();
  }

  public String checksum() {
    return new StableHash().addString(canonicalText()).finishHex();
  }
}
