package com.hgtech.soma.examples.grassing.validation;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;

/** 不依赖 live runtime 的 detached Result 断言。 */
public final class SimulationResultAssertions {
  private SimulationResultAssertions() {
  }

  public static void validate(
      SimulationScenario scenario, SimulationResult result) {
    SimulationConfig config = scenario.config();
    require(result.ticks() == config.ticks(), "completed tick mismatch");
    require(result.configChecksum().equals(config.checksum()),
        "config checksum mismatch");
    require(result.inputChecksum().equals(scenario.checksum()),
        "input checksum mismatch");
    require(result.population() >= 0
            && result.grassingPopulation() >= 0
            && result.searchingPopulation() >= 0,
        "negative population summary");
    require(result.grassingPopulation() + result.searchingPopulation()
            == result.population(),
        "mode counts do not cover population");
    require(Math.addExact(config.initialPopulation(), result.births())
            - result.deaths() == result.population(),
        "population conservation mismatch");
    require(Double.isFinite(result.totalGrass())
            && Double.isFinite(result.totalEnergy()),
        "non-finite result aggregate");
    require(result.resultChecksum().length() == 64,
        "result checksum is not sha-256 hex");
    require(result.diagnostics().schemaHash().length() == 64
            && result.diagnostics().runtimePlanHash().length() == 64,
        "runtime identity is not sha-256 hex");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
