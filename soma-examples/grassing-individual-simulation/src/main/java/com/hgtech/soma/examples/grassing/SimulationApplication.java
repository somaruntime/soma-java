package com.hgtech.soma.examples.grassing;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.config.SimulationConfigLoader;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenario;
import com.hgtech.soma.examples.grassing.scenario.SimulationScenarioFactory;
import com.hgtech.soma.examples.grassing.scenario.SyntheticSimulationScenarioFactory;
import com.hgtech.soma.examples.grassing.simulation.Simulator;
import com.hgtech.soma.examples.grassing.simulation.SomaSimulator;

import java.util.Arrays;

/** 可重放的 headless grasser–grass 参考应用入口。 */
public final class SimulationApplication {
  private SimulationApplication() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    String[] overrides = args.length <= 1
        ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    SimulationConfig config =
        new SimulationConfigLoader().load(selector, overrides);
    SimulationScenarioFactory scenarioFactory =
        new SyntheticSimulationScenarioFactory();
    SimulationScenario scenario = scenarioFactory.create(config);
    System.out.print(config.canonicalText());
    System.out.println("config.checksum=" + config.checksum());
    System.out.println("input.checksum=" + scenario.checksum());

    Simulator simulator = new SomaSimulator();
    SimulationResult result = simulator.run(scenario);
    System.out.println("ticks=" + result.ticks());
    System.out.println("population=" + result.population());
    System.out.println("maximum.population=" + result.maximumPopulation());
    System.out.println("births=" + result.births());
    System.out.println("deaths=" + result.deaths());
    System.out.println("total.grass=" + result.totalGrass());
    System.out.println("total.energy=" + result.totalEnergy());
    System.out.println("result.checksum=" + result.resultChecksum());
    System.out.println("claimAllowed=false");
  }
}
