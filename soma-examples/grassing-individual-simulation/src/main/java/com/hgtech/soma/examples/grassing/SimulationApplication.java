package com.hgtech.soma.examples.grassing;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.model.InitialStateGenerator;
import com.hgtech.soma.examples.grassing.model.SimulationInitialState;
import com.hgtech.soma.examples.grassing.runtime.SimulationEngine;
import com.hgtech.soma.examples.grassing.runtime.SimulationResult;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntime;
import com.hgtech.soma.examples.grassing.runtime.SimulationRuntimeBootstrap;
import com.hgtech.soma.examples.grassing.validation.SimulationValidator;

import java.util.Arrays;

/** 可重放的 headless grasser–grass 参考应用入口。 */
public final class SimulationApplication {
  private SimulationApplication() {
  }

  public static void main(String[] args) throws Exception {
    String selector = args.length == 0 ? "default" : args[0];
    String[] overrides = args.length <= 1
        ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    SimulationConfig config = SimulationConfig.load(selector, overrides);
    SimulationInitialState initialState =
        InitialStateGenerator.generate(config);
    System.out.print(config.canonicalText());
    System.out.println("config.checksum=" + config.checksum());
    System.out.println("input.checksum=" + initialState.checksum());

    SimulationRuntime runtime =
        SimulationRuntimeBootstrap.load(config, initialState);
    try {
      SimulationResult result = new SimulationEngine(runtime).run();
      SimulationValidator.validate(config, runtime, result);
      System.out.println("ticks=" + result.ticks);
      System.out.println("population=" + result.population);
      System.out.println("maximum.population=" + result.maximumPopulation);
      System.out.println("births=" + result.births);
      System.out.println("deaths=" + result.deaths);
      System.out.println("total.grass=" + result.totalGrass);
      System.out.println("total.energy=" + result.totalEnergy);
      System.out.println("result.checksum=" + result.resultChecksum);
      System.out.println("claimAllowed=false");
    } finally {
      runtime.close();
    }
  }
}
