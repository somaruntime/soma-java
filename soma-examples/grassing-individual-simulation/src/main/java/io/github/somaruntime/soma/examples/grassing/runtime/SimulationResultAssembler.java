package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.result.SimulationDiagnostics;
import io.github.somaruntime.soma.examples.grassing.result.SimulationResult;
import io.github.somaruntime.soma.examples.grassing.schema.BehaviourMode;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateCursor;
import io.github.somaruntime.soma.examples.grassing.schema.generated.GrasserStateScan;
import io.github.somaruntime.soma.examples.grassing.support.StableHash;

/** 从 authoritative runtime 组装 detached summary Result。 */
final class SimulationResultAssembler {
  private static final GrasserStateScan.Comparator STABLE_ID_ORDER =
      new GrasserStateScan.Comparator() {
        @Override
        public int compare(
            GrasserStateCursor left, GrasserStateCursor right) {
          return Long.compare(
              left.grasserIdValue(), right.grasserIdValue());
        }
      };

  private final SimulationRuntime runtime;

  SimulationResultAssembler(SimulationRuntime runtime) {
    this.runtime = runtime;
  }

  SimulationResult create(
      long tick, long births, long deaths, int maximumPopulation) {
    ResultProjection projection =
        projectResult(tick, births, deaths);
    SimulationSummary summary = projection.summary;
    return new SimulationResult(
        (int) tick, runtime.grassers.size(), maximumPopulation,
        summary.grassing, summary.searching, births, deaths,
        summary.totalGrass, summary.totalEnergy,
        runtime.config.checksum(), runtime.inputChecksum,
        projection.checksum, diagnostics());
  }

  SimulationSummary summary() {
    EnergyAccumulator energy = new EnergyAccumulator();
    visitByStableId(energy);
    ModeCounts modes = modeCounts();
    double totalGrass = totalGrass();
    requireFinite(totalGrass, "total grass");
    requireFinite(energy.total, "total energy");
    return new SimulationSummary(
        totalGrass, energy.total, modes.grassing, modes.searching);
  }

  private ResultProjection projectResult(
      long tick, long births, long deaths) {
    StableHash hash = new StableHash()
        .addString("grassing-simulation-result-v1")
        .addLong(tick).addLong(births).addLong(deaths)
        .addInt(runtime.config.width()).addInt(runtime.config.height());
    double totalGrass = 0.0;
    for (double value : runtime.grass) {
      totalGrass += value;
      hash.addDouble(value);
    }
    ResultAccumulator individuals = new ResultAccumulator(hash);
    visitByStableId(individuals);
    ModeCounts modes = modeCounts();
    requireFinite(totalGrass, "total grass");
    requireFinite(individuals.totalEnergy, "total energy");
    return new ResultProjection(
        new SimulationSummary(
            totalGrass,
            individuals.totalEnergy,
            modes.grassing,
            modes.searching),
        individuals.finishChecksum());
  }

  private void visitByStableId(GrasserStateScan.Consumer consumer) {
    runtime.grassers.sorted(STABLE_ID_ORDER).forEach(consumer);
  }

  private ModeCounts modeCounts() {
    int grassing = Math.toIntExact(
        runtime.grassers.scanByMode(BehaviourMode.GRASSING).count());
    int searching = Math.toIntExact(
        runtime.grassers.scanByMode(BehaviourMode.SEARCHING).count());
    if (grassing + searching != runtime.grassers.size()) {
      throw new IllegalStateException("mode groups do not cover population");
    }
    return new ModeCounts(grassing, searching);
  }

  private double totalGrass() {
    double total = 0.0;
    for (double value : runtime.grass) total += value;
    return total;
  }

  private SimulationDiagnostics diagnostics() {
    return runtime.diagnostics();
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " is not finite");
    }
  }

  private static final class EnergyAccumulator
      implements GrasserStateScan.Consumer {
    double total;

    @Override
    public void accept(GrasserStateCursor candidate) {
      total += candidate.energy();
    }
  }

  private static final class ResultAccumulator
      implements GrasserStateScan.Consumer {
    private final StableHash hash;
    double totalEnergy;

    ResultAccumulator(StableHash hash) {
      this.hash = hash;
    }

    @Override
    public void accept(GrasserStateCursor candidate) {
      totalEnergy += candidate.energy();
      hash.addLong(candidate.grasserIdValue())
          .addInt(candidate.x()).addInt(candidate.y())
          .addDouble(candidate.energy())
          .addInt(candidate.mode().ordinal())
          .addInt(candidate.movementDirection());
    }

    String finishChecksum() {
      return hash.finishHex();
    }
  }

  private static final class ModeCounts {
    final int grassing;
    final int searching;

    ModeCounts(int grassing, int searching) {
      this.grassing = grassing;
      this.searching = searching;
    }
  }

  private static final class ResultProjection {
    final SimulationSummary summary;
    final String checksum;

    ResultProjection(SimulationSummary summary, String checksum) {
      this.summary = summary;
      this.checksum = checksum;
    }
  }
}
