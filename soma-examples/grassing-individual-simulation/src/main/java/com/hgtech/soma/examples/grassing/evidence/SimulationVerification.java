package com.hgtech.soma.examples.grassing.evidence;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.config.SimulationConfigLoader;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationEngine;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntimeBootstrap;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntimeChecks;
import com.hgtech.soma.examples.grassing.scenario.IndividualSeed;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.scenario.SyntheticSimulationScenarioFactory;
import com.hgtech.soma.examples.grassing.validation.SimulationValidator;

import java.util.ArrayList;
import java.util.Collections;

/** 无 testkit/JUnit 依赖的 correctness、replay 和 long-run 入口。 */
public final class SimulationVerification {
  private SimulationVerification() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "correctness" : args[0];
    SimulationConfig config = new SimulationConfigLoader().load(selector);
    SyntheticSimulationScenarioFactory factory =
        new SyntheticSimulationScenarioFactory();
    SimulationScenario first = factory.create(config);
    SimulationScenario repeat = factory.create(config);
    require(first.checksum().equals(repeat.checksum()),
        "initial-state generator is not deterministic");

    Run run = execute(config, first);
    if (isReplayProfile(selector)) {
      Run repeated = execute(config, repeat);
      Run reordered = execute(config, reversed(first));
      require(run.result.resultChecksum().equals(
              repeated.result.resultChecksum()),
          "runtime result is not replayable");
      require(run.result.resultChecksum().equals(
              reordered.result.resultChecksum()),
          "runtime result depends on initial packed order");
      require(first.checksum().equals(
              reversed(first).checksum()),
          "input checksum depends on physical order");
    }

    String oracle = "not-applicable";
    if (isCorrectnessProfile(selector)) {
      oracle = verifyAoS(config, first);
      SimulationValidator.verifyInvalidInputs(config);
      SimulationRuntimeChecks.verify(config, first);
    }
    System.out.println("simulation-verification: profile=" + selector
        + " ticks=" + run.result.ticks()
        + " population=" + run.result.population()
        + " inputChecksum=" + first.checksum()
        + " resultChecksum=" + run.result.resultChecksum()
        + " oracle=" + oracle
        + " births=" + run.result.births()
        + " deaths=" + run.result.deaths()
        + " claimAllowed=false");
  }

  private static String verifyAoS(
      SimulationConfig config, SimulationScenario scenario) {
    SimulationRuntime runtime = SimulationRuntimeBootstrap.load(scenario);
    try {
      SimulationEngine engine = new SimulationEngine(runtime);
      com.hgtech.soma.examples.grassing.validation.ReferenceSimulationBridge
          bridge =
          new com.hgtech.soma.examples.grassing.validation
              .ReferenceSimulationBridge(config, scenario);
      while (engine.currentTick() < config.ticks()) {
        engine.step();
        bridge.stepAndAssert(runtime, engine.currentResult());
      }
      SimulationResult result = engine.run();
      SimulationValidator.validate(config, runtime, result);
      return result.resultChecksum();
    } finally {
      runtime.close();
    }
  }

  private static Run execute(
      SimulationConfig config, SimulationScenario scenario) {
    SimulationRuntime runtime = SimulationRuntimeBootstrap.load(scenario);
    try {
      SimulationEngine engine = new SimulationEngine(runtime);
      SimulationResult result = engine.run();
      SimulationValidator.validate(config, runtime, result);
      boolean oneShotRejected = false;
      try {
        engine.run();
      } catch (IllegalStateException expected) {
        oneShotRejected = true;
      }
      require(oneShotRejected, "one-shot engine accepted a second run");
      return new Run(result);
    } finally {
      runtime.close();
    }
  }

  private static SimulationScenario reversed(SimulationScenario scenario) {
    ArrayList<IndividualSeed> individuals =
        new ArrayList<IndividualSeed>(scenario.individuals());
    Collections.reverse(individuals);
    return new SimulationScenario(
        scenario.config(), scenario.grassCopy(), individuals);
  }

  private static boolean isCorrectnessProfile(String selector) {
    return "correctness".equals(selector)
        || selector.endsWith("correctness.properties");
  }

  private static boolean isReplayProfile(String selector) {
    return isCorrectnessProfile(selector)
        || "default".equals(selector)
        || selector.endsWith("default.properties");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class Run {
    final SimulationResult result;

    Run(SimulationResult result) {
      this.result = result;
    }
  }
}
