package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.result.SimulationResult;

/**
 * 固定编排 growth → metabolism/death → reproduction → grassing → searching
 * → trace。
 */
public final class SimulationEngine {
  private final SimulationRuntime runtime;
  private final SimulationConfig config;
  private final GrassGrowthSystem grassGrowth;
  private final MetabolismSystem metabolism;
  private final ReproductionSystem reproduction;
  private final GrassingSystem grassing;
  private final SearchingSystem searching;
  private final SimulationResultAssembler resultAssembler;
  private final TraceRecorder traceRecorder;
  private long tick;
  private long births;
  private long deaths;
  private int maximumPopulation;
  private boolean completed;

  public SimulationEngine(SimulationRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    this.runtime = runtime;
    config = runtime.config;
    grassGrowth = new GrassGrowthSystem(runtime);
    metabolism = new MetabolismSystem(runtime);
    reproduction = new ReproductionSystem(runtime);
    grassing = new GrassingSystem(runtime);
    searching = new SearchingSystem(runtime);
    resultAssembler = new SimulationResultAssembler(runtime);
    traceRecorder = new TraceRecorder(runtime, resultAssembler);
    maximumPopulation = runtime.grassers.size();
    traceRecorder.record(0L, births, deaths);
  }

  public SimulationResult run() {
    if (completed) {
      throw new IllegalStateException("simulation engine is one-shot");
    }
    while (tick < config.ticks()) step();
    completed = true;
    return result();
  }

  public boolean hasNextTick() {
    return !completed && tick < config.ticks();
  }

  public void step() {
    if (completed || tick >= config.ticks()) {
      throw new IllegalStateException("simulation has no remaining tick");
    }
    tick++;
    grassGrowth.execute();
    deaths = Math.addExact(deaths, metabolism.execute());
    births = Math.addExact(births, reproduction.execute(tick));
    grassing.execute();
    searching.execute(tick);
    maximumPopulation = Math.max(maximumPopulation, runtime.grassers.size());
    if (tick % config.traceInterval() == 0L || tick == config.ticks()) {
      traceRecorder.record(tick, births, deaths);
    }
  }

  public long currentTick() {
    return tick;
  }

  public SimulationResult currentResult() {
    return result();
  }

  private SimulationResult result() {
    return resultAssembler.create(
        tick, births, deaths, maximumPopulation);
  }
}
