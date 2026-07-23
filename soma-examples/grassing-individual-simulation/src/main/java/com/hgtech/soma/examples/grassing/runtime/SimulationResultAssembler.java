package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.result.SimulationDiagnostics;
import com.hgtech.soma.examples.grassing.result.SimulationResult;
import com.hgtech.soma.examples.grassing.schema.BehaviourMode;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateCursor;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateScan;
import com.hgtech.soma.examples.grassing.support.StableHash;

/** 从 authoritative runtime 组装 detached summary Result。 */
final class SimulationResultAssembler {
  private final SimulationRuntime runtime;
  private StableHash activeHash;
  private double aggregateEnergy;

  SimulationResultAssembler(SimulationRuntime runtime) {
    this.runtime = runtime;
  }

  SimulationResult create(
      long tick, long births, long deaths, int maximumPopulation) {
    SimulationSummary summary = summary();
    return new SimulationResult(
        (int) tick, runtime.grassers.size(), maximumPopulation,
        summary.grassing, summary.searching, births, deaths,
        summary.totalGrass, summary.totalEnergy,
        runtime.config.checksum(), runtime.inputChecksum,
        checksum(tick, births, deaths), diagnostics());
  }

  SimulationSummary summary() {
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
    return new SimulationSummary(
        totalGrass, aggregateEnergy, grassing, searching);
  }

  private String checksum(long tick, long births, long deaths) {
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

  private SimulationDiagnostics diagnostics() {
    return runtime.diagnostics();
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " is not finite");
    }
  }
}
