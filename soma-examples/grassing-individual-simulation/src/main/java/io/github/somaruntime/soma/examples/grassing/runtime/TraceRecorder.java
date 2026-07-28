package io.github.somaruntime.soma.examples.grassing.runtime;

import io.github.somaruntime.soma.examples.grassing.schema.generated.TraceSampleBatch;

/** 在配置边界记录稳定 trace。 */
final class TraceRecorder {
  private final SimulationRuntime runtime;
  private final SimulationResultAssembler resultAssembler;
  private final TraceSampleBatch batch = new TraceSampleBatch(1);

  TraceRecorder(
      SimulationRuntime runtime, SimulationResultAssembler resultAssembler) {
    this.runtime = runtime;
    this.resultAssembler = resultAssembler;
  }

  void record(long completedTick, long births, long deaths) {
    SimulationSummary summary = resultAssembler.summary();
    batch.clear();
    batch.addValues(completedTick, runtime.grassers.size(),
        summary.totalGrass, summary.totalEnergy,
        summary.grassing, summary.searching, births, deaths);
    runtime.traces.addBatch(batch);
  }
}
