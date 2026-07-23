package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.result.SimulationDiagnostics;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.state.BehaviourMode;
import com.hgtech.soma.examples.grassing.state.GrasserId;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateBatch;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateCursor;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateScan;
import com.hgtech.soma.examples.grassing.state.generated.GrasserStateUpdateCursor;
import com.hgtech.soma.examples.grassing.state.generated.TraceSampleBatch;
import com.hgtech.soma.examples.grassing.support.DeterministicRandom;
import com.hgtech.soma.examples.grassing.support.StableHash;

import java.util.Arrays;

/**
 * 显式执行 growth → metabolism/death → reproduction → grassing → searching。
 */
public final class SimulationEngine {
  private static final int REPRODUCTION_PROCESS = 20;
  private static final int CHILD_DIRECTION_PROCESS = 21;
  private static final int MOVEMENT_PROCESS = 22;

  private final SimulationRuntime runtime;
  private final SimulationConfig config;
  private final GrasserStateBatch offspringBatch = new GrasserStateBatch(256);
  private final TraceSampleBatch traceBatch = new TraceSampleBatch(1);
  private int[] stagedX = new int[256];
  private int[] stagedY = new int[256];
  private double[] stagedEnergy = new double[256];
  private int stagedCount;
  private long tick;
  private long births;
  private long deaths;
  private int maximumPopulation;
  private boolean completed;
  private StableHash activeHash;
  private double aggregateEnergy;

  public SimulationEngine(SimulationRuntime runtime) {
    if (runtime == null) throw new NullPointerException("runtime");
    this.runtime = runtime;
    this.config = runtime.config;
    this.maximumPopulation = runtime.grassers.size();
    recordTrace(0L);
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
    growGrass();
    metabolizeAndRemoveDead();
    reproduce();
    grass();
    search();
    maximumPopulation = Math.max(maximumPopulation, runtime.grassers.size());
    if (tick % config.traceInterval() == 0L || tick == config.ticks()) {
      recordTrace(tick);
    }
  }

  public long currentTick() {
    return tick;
  }

  public SimulationResult currentResult() {
    return result();
  }

  private void growGrass() {
    double capacity = config.grassCarryingCapacity();
    double rate = config.grassGrowthRate();
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      double grass = runtime.grass[cell];
      double grown = grass + rate * grass * (1.0 - grass / capacity);
      runtime.grass[cell] = Math.min(capacity, Math.max(0.0, grown));
    }
  }

  private void metabolizeAndRemoveDead() {
    runtime.grassers.update(new GrasserStateScan.Updater() {
      @Override
      public void update(GrasserStateUpdateCursor candidate) {
        candidate.setEnergy(candidate.energy() - config.metabolismCost());
      }
    });
    int before = runtime.grassers.size();
    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return candidate.energy() <= 0.0;
      }
    }).remove();
    deaths = Math.addExact(deaths, before - runtime.grassers.size());
  }

  private void reproduce() {
    stagedCount = 0;
    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return reproductionCandidate(candidate);
      }
    }).sorted(new GrasserStateScan.Comparator() {
      @Override
      public int compare(GrasserStateCursor left, GrasserStateCursor right) {
        return Long.compare(left.grasserIdValue(), right.grasserIdValue());
      }
    }).forEach(new GrasserStateScan.Consumer() {
      @Override
      public void accept(GrasserStateCursor candidate) {
        stageParent(candidate);
      }
    });
    if (stagedCount == 0) return;

    offspringBatch.clear();
    long nextId = runtime.nextId();
    for (int index = 0; index < stagedCount; index++) {
      long childId = Math.addExact(nextId, index);
      int direction = DeterministicRandom.bounded(
          config.seed(), tick, childId, CHILD_DIRECTION_PROCESS, 0, 4);
      offspringBatch.addValues(new GrasserId(childId),
          stagedX[index], stagedY[index], stagedEnergy[index] * 0.5,
          BehaviourMode.GRASSING, direction);
    }

    runtime.grassers.filter(new GrasserStateScan.Predicate() {
      @Override
      public boolean test(GrasserStateCursor candidate) {
        return reproductionCandidate(candidate);
      }
    }).update(new GrasserStateScan.Updater() {
      @Override
      public void update(GrasserStateUpdateCursor candidate) {
        candidate.setEnergy(candidate.energy() * 0.5);
      }
    });
    runtime.grassers.addBatch(offspringBatch);
    runtime.nextId(Math.addExact(nextId, stagedCount));
    births = Math.addExact(births, stagedCount);
  }

  private boolean reproductionCandidate(GrasserStateCursor candidate) {
    return candidate.energy() >= config.reproductionThreshold()
        && DeterministicRandom.unit(
            config.seed(), tick, candidate.grasserIdValue(),
            REPRODUCTION_PROCESS, 0) < config.reproductionProbability();
  }

  private void stageParent(GrasserStateCursor candidate) {
    ensureStaging(stagedCount + 1);
    stagedX[stagedCount] = candidate.x();
    stagedY[stagedCount] = candidate.y();
    stagedEnergy[stagedCount] = candidate.energy();
    stagedCount++;
  }

  private void ensureStaging(int required) {
    if (required <= stagedX.length) return;
    int next = Math.max(required, stagedX.length * 3 / 2 + 1);
    stagedX = Arrays.copyOf(stagedX, next);
    stagedY = Arrays.copyOf(stagedY, next);
    stagedEnergy = Arrays.copyOf(stagedEnergy, next);
  }

  private void grass() {
    Arrays.fill(runtime.cellPopulation, 0);
    Arrays.fill(runtime.cellShare, 0.0);
    Arrays.fill(runtime.cellConsumption, 0.0);
    runtime.grassers.scanByMode(BehaviourMode.GRASSING)
        .forEach(new GrasserStateScan.Consumer() {
          @Override
          public void accept(GrasserStateCursor candidate) {
            int cell = runtime.cell(candidate.x(), candidate.y());
            runtime.cellPopulation[cell] =
                Math.addExact(runtime.cellPopulation[cell], 1);
          }
        });
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      int consumers = runtime.cellPopulation[cell];
      if (consumers == 0) continue;
      double consumable = Math.max(
          0.0, runtime.grass[cell] - config.grassRegrowthFloor());
      double share = Math.min(
          config.grassingAmount(), consumable / consumers);
      runtime.cellShare[cell] = share;
      runtime.cellConsumption[cell] = share * consumers;
      requireFinite(share, "grassing share");
    }
    runtime.grassers.scanByMode(BehaviourMode.GRASSING)
        .update(new GrasserStateScan.Updater() {
          @Override
          public void update(GrasserStateUpdateCursor candidate) {
            int cell = runtime.cell(candidate.x(), candidate.y());
            double share = runtime.cellShare[cell];
            candidate.setEnergy(candidate.energy() + share);
            if (share < config.grassingAmount() * 0.5) {
              candidate.setMode(BehaviourMode.SEARCHING);
            }
          }
        });
    for (int cell = 0; cell < runtime.grass.length; cell++) {
      double remaining =
          runtime.grass[cell] - runtime.cellConsumption[cell];
      runtime.grass[cell] =
          Math.max(config.grassRegrowthFloor(), remaining);
    }
  }

  private void search() {
    runtime.grassers.scanByMode(BehaviourMode.SEARCHING)
        .update(new GrasserStateScan.Updater() {
          @Override
          public void update(GrasserStateUpdateCursor candidate) {
            int direction = DeterministicRandom.bounded(
                config.seed(), tick, candidate.grasserIdValue(),
                MOVEMENT_PROCESS, 0, 4);
            int x = candidate.x();
            int y = candidate.y();
            if (direction == 0) {
              x = (x + 1) % config.width();
            } else if (direction == 1) {
              x = (x + config.width() - 1) % config.width();
            } else if (direction == 2) {
              y = (y + 1) % config.height();
            } else {
              y = (y + config.height() - 1) % config.height();
            }
            candidate.setX(x);
            candidate.setY(y);
            candidate.setMovementDirection(direction);
            double grass = runtime.grass[runtime.cell(x, y)];
            if (candidate.energy() <= config.searchEnergyThreshold()
                || grass >= config.grassingAmount() * 0.5) {
              candidate.setMode(BehaviourMode.GRASSING);
            }
          }
        });
  }

  private void recordTrace(long completedTick) {
    Summary summary = summary();
    traceBatch.clear();
    traceBatch.addValues(completedTick, runtime.grassers.size(),
        summary.totalGrass, summary.totalEnergy,
        summary.grassing, summary.searching, births, deaths);
    runtime.traces.addBatch(traceBatch);
  }

  private SimulationResult result() {
    Summary summary = summary();
    return new SimulationResult(
        (int) tick, runtime.grassers.size(), maximumPopulation,
        summary.grassing, summary.searching, births, deaths,
        summary.totalGrass, summary.totalEnergy,
        config.checksum(), runtime.inputChecksum, checksum(),
        diagnostics());
  }

  private SimulationDiagnostics diagnostics() {
    SimulationRuntime.RuntimeEvidence evidence = runtime.runtimeEvidence();
    return new SimulationDiagnostics(
        runtime.schemaHash(), runtime.runtimePlanHash(),
        evidence.exactIndexHighWaterBytes,
        evidence.updateScratchHighWaterBytes,
        evidence.operationScratchHighWaterBytes,
        evidence.populationGrowthCount,
        evidence.populationCapacity,
        evidence.traceCapacity);
  }

  private Summary summary() {
    double totalGrass = 0.0;
    for (double value : runtime.grass) totalGrass += value;
    aggregateEnergy = 0.0;
    runtime.grassers.sorted(new GrasserStateScan.Comparator() {
      @Override
      public int compare(GrasserStateCursor left, GrasserStateCursor right) {
        return Long.compare(left.grasserIdValue(), right.grasserIdValue());
      }
    }).forEach(new GrasserStateScan.Consumer() {
      @Override
      public void accept(GrasserStateCursor candidate) {
        aggregateEnergy += candidate.energy();
      }
    });
    int grassing = Math.toIntExact(
        runtime.grassers.scanByMode(BehaviourMode.GRASSING).count());
    int searching = Math.toIntExact(
        runtime.grassers.scanByMode(BehaviourMode.SEARCHING).count());
    if (grassing + searching != runtime.grassers.size()) {
      throw new IllegalStateException("mode groups do not cover population");
    }
    requireFinite(totalGrass, "total grass");
    requireFinite(aggregateEnergy, "total energy");
    return new Summary(totalGrass, aggregateEnergy, grassing, searching);
  }

  private String checksum() {
    activeHash = new StableHash()
        .addString("grassing-simulation-result-v1")
        .addLong(tick).addLong(births).addLong(deaths)
        .addInt(runtime.config.width()).addInt(runtime.config.height());
    for (double value : runtime.grass) activeHash.addDouble(value);
    runtime.grassers.sorted(new GrasserStateScan.Comparator() {
      @Override
      public int compare(GrasserStateCursor left, GrasserStateCursor right) {
        return Long.compare(left.grasserIdValue(), right.grasserIdValue());
      }
    }).forEach(new GrasserStateScan.Consumer() {
      @Override
      public void accept(GrasserStateCursor candidate) {
        activeHash.addLong(candidate.grasserIdValue())
            .addInt(candidate.x()).addInt(candidate.y())
            .addDouble(candidate.energy())
            .addInt(candidate.mode().ordinal())
            .addInt(candidate.movementDirection());
      }
    });
    String checksum = activeHash.finishHex();
    activeHash = null;
    return checksum;
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " is not finite");
    }
  }

  private static final class Summary {
    final double totalGrass;
    final double totalEnergy;
    final int grassing;
    final int searching;

    Summary(double totalGrass, double totalEnergy,
            int grassing, int searching) {
      this.totalGrass = totalGrass;
      this.totalEnergy = totalEnergy;
      this.grassing = grassing;
      this.searching = searching;
    }
  }
}
