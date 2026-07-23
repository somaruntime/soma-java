package com.hgtech.soma.examples.grassing.validation;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.config.SimulationConfigLoader;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;
import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.schema.BehaviourMode;
import com.hgtech.soma.examples.grassing.schema.GrasserState;
import com.hgtech.soma.examples.grassing.schema.TraceSample;
import com.hgtech.soma.examples.grassing.validation.ReferenceSimulation.Individual;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 领域 invariant、trace 和 tiny AoS 等价验证。 */
public final class SimulationValidator {
  private SimulationValidator() {
  }

  public static void validate(
      SimulationConfig config, SimulationRuntime runtime,
      SimulationResult result) {
    double[] grass = runtime.grassCopy();
    double totalGrass = 0.0;
    for (double value : grass) {
      require(Double.isFinite(value), "grass is not finite");
      require(value >= 0.0 && value <= config.grassCarryingCapacity(),
          "grass is outside domain bounds");
      totalGrass += value;
    }
    require(equalBits(totalGrass, result.totalGrass()),
        "result grass total differs from authoritative grid");

    List<GrasserState> individuals = runtime.materializeIndividuals();
    Set<Long> identities = new HashSet<Long>(
        Math.max(1, individuals.size() * 4 / 3 + 1));
    double totalEnergy = 0.0;
    int grassing = 0;
    int searching = 0;
    for (GrasserState individual : individuals) {
      require(individual.grasserId != null
              && individual.grasserId.value > 0L,
          "missing or invalid identity");
      require(identities.add(Long.valueOf(individual.grasserId.value)),
          "duplicate identity");
      require(individual.x >= 0 && individual.x < config.width()
              && individual.y >= 0 && individual.y < config.height(),
          "individual position out of bounds");
      require(Double.isFinite(individual.energy) && individual.energy > 0.0,
          "live individual energy is invalid");
      require(individual.movementDirection >= 0
              && individual.movementDirection < 4,
          "movement direction out of range");
      if (individual.mode == BehaviourMode.GRASSING) {
        grassing++;
      } else if (individual.mode == BehaviourMode.SEARCHING) {
        searching++;
      } else {
        throw new IllegalStateException("unknown behaviour mode");
      }
    }
    Collections.sort(individuals, INDIVIDUAL_ORDER);
    for (GrasserState individual : individuals) totalEnergy += individual.energy;
    require(equalBits(totalEnergy, result.totalEnergy()),
        "result energy total differs from canonical individual order");
    require(individuals.size() == result.population()
            && grassing == result.grassingPopulation()
            && searching == result.searchingPopulation(),
        "result population summary mismatch");
    require(Math.addExact(config.initialPopulation(), result.births())
            - result.deaths() == result.population(),
        "population conservation mismatch");

    require(runtime.keyCount() == result.population(),
        "key traversal does not cover population");
    double snapshotEnergy = runtime.snapshotEnergySum();
    require(close(snapshotEnergy, result.totalEnergy(), result.population()),
        "IndexSnapshot column gather mismatch");

    List<TraceSample> traces = runtime.materializeTraces();
    require(!traces.isEmpty(), "trace is empty");
    long previousTick = -1L;
    for (TraceSample sample : traces) {
      require(sample.tick > previousTick, "trace tick is not increasing");
      require(sample.population >= 0
              && sample.grassingPopulation >= 0
              && sample.searchingPopulation >= 0,
          "negative trace population");
      require(sample.grassingPopulation + sample.searchingPopulation
              == sample.population,
          "trace mode counts do not cover population");
      require(Double.isFinite(sample.totalGrass)
              && Double.isFinite(sample.totalEnergy),
          "trace aggregate is not finite");
      previousTick = sample.tick;
    }
    TraceSample last = traces.get(traces.size() - 1);
    require(last.tick == result.ticks()
            && last.population == result.population()
            && last.births == result.births()
            && last.deaths == result.deaths()
            && equalBits(last.totalGrass, result.totalGrass())
            && equalBits(last.totalEnergy, result.totalEnergy()),
        "final trace differs from result");
  }

  static void assertEquivalent(
      SimulationRuntime runtime, ReferenceSimulation reference,
      SimulationResult runtimeResult) {
    require(runtimeResult.ticks() == reference.tick(),
        "AoS tick mismatch");
    require(runtimeResult.births() == reference.births()
            && runtimeResult.deaths() == reference.deaths(),
        "AoS birth/death mismatch");
    double[] actualGrass = runtime.grassCopy();
    double[] expectedGrass = reference.grassCopy();
    require(actualGrass.length == expectedGrass.length,
        "AoS grass length mismatch");
    for (int cell = 0; cell < actualGrass.length; cell++) {
      require(equalBits(actualGrass[cell], expectedGrass[cell]),
          "AoS grass mismatch at cell " + cell);
    }

    List<GrasserState> actual = runtime.materializeIndividuals();
    Collections.sort(actual, INDIVIDUAL_ORDER);
    List<Individual> expected = reference.canonicalIndividuals();
    require(actual.size() == expected.size(), "AoS population mismatch");
    for (int index = 0; index < actual.size(); index++) {
      GrasserState left = actual.get(index);
      Individual right = expected.get(index);
      require(left.grasserId.value == right.id
              && left.x == right.x && left.y == right.y
              && equalBits(left.energy, right.energy)
              && left.mode.ordinal() == right.mode
              && left.movementDirection == right.direction,
          "AoS individual mismatch at canonical position " + index);
    }
  }

  public static void verifyInvalidInputs(SimulationConfig correctness)
      throws Exception {
    expectFailure(new Action() {
      @Override
      public void run() throws Exception {
        new SimulationConfigLoader().load(
            "correctness", "world.width=0");
      }
    }, "zero world width");
    expectFailure(new Action() {
      @Override
      public void run() throws Exception {
        new SimulationConfigLoader().load(
            "correctness", "grass.growth.rate=NaN");
      }
    }, "NaN growth rate");
    expectFailure(new Action() {
      @Override
      public void run() throws Exception {
        double[] grass = new double[] {0.5};
        IndividualSeed first =
            new IndividualSeed(
                1L, 0, 0, 1.0,
                IndividualSeed.MODE_GRASSING, 0);
        SimulationConfig config = new SimulationConfigLoader().load(
            "correctness", "world.width=1", "world.height=1",
            "initial.population=2");
        new SimulationScenario(config, grass,
            java.util.Arrays.asList(first, first));
      }
    }, "duplicate identity");
    expectFailure(new Action() {
      @Override
      public void run() throws Exception {
        SimulationConfig config = new SimulationConfigLoader().load(
            "correctness", "world.width=1", "world.height=1",
            "initial.population=1");
        new SimulationScenario(config, new double[] {Double.NaN},
            java.util.Collections.singletonList(new IndividualSeed(
                1L, 0, 0, 1.0, IndividualSeed.MODE_GRASSING, 0)));
      }
    }, "NaN initial grass");
    require(correctness.configVersion() == 1,
        "correctness config unexpectedly changed during negative tests");
  }

  private static void expectFailure(Action action, String label)
      throws Exception {
    boolean rejected = false;
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    require(rejected, "invalid input was accepted: " + label);
  }

  private static boolean equalBits(double left, double right) {
    return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
  }

  private static boolean close(double left, double right, int terms) {
    double scale = Math.max(1.0, Math.max(Math.abs(left), Math.abs(right)));
    return Math.abs(left - right)
        <= Math.max(1, terms) * Math.ulp(scale) * 2.0;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private interface Action {
    void run() throws Exception;
  }

  private static final Comparator<GrasserState> INDIVIDUAL_ORDER =
      new Comparator<GrasserState>() {
        @Override
        public int compare(GrasserState left, GrasserState right) {
          return Long.compare(left.grasserId.value, right.grasserId.value);
        }
      };
}
