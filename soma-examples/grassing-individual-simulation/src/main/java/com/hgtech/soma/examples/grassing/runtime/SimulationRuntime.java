package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.result.SimulationDiagnostics;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.schema.generated.TraceSampleTable;
import com.hgtech.soma.runtime.SomaGroup;
import com.hgtech.soma.runtime.TableStats;
import com.hgtech.soma.runtime.metadata.SomaGroupMetadata;

import java.util.Arrays;

/** 一次 simulation session 的 SOMA tables、grass grid 与 scratch owner。 */
public final class SimulationRuntime implements AutoCloseable {
  final SimulationConfig config;
  final String inputChecksum;
  final SomaGroup group;
  final GrasserStateTable grassers;
  final TraceSampleTable traces;
  final double[] grass;
  final int[] cellPopulation;
  final double[] cellShare;
  final double[] cellConsumption;
  private long nextId;
  private boolean closed;

  SimulationRuntime(SimulationConfig config, String inputChecksum,
                    SomaGroup group,
                    GrasserStateTable grassers,
                    TraceSampleTable traces,
                    double[] grass) {
    this.config = config;
    this.inputChecksum = inputChecksum;
    this.group = group;
    this.grassers = grassers;
    this.traces = traces;
    this.grass = grass;
    this.cellPopulation = new int[grass.length];
    this.cellShare = new double[grass.length];
    this.cellConsumption = new double[grass.length];
  }

  void initializeNextId(long value) {
    if (value <= 0L) throw new IllegalArgumentException("next id overflow");
    nextId = value;
  }

  long nextId() {
    ensureOpen();
    return nextId;
  }

  void nextId(long value) {
    if (value <= 0L) throw new IllegalArgumentException("next id overflow");
    nextId = value;
  }

  int cell(int x, int y) {
    return y * config.width() + x;
  }

  SimulationDiagnostics diagnostics() {
    ensureOpen();
    TableStats stateStats = grassers.statsSnapshot();
    TableStats traceStats = traces.statsSnapshot();
    return new SimulationDiagnostics(
        grassers.runtimePlan().schemaHash(),
        grassers.runtimePlan().runtimePlanHash(),
        stateStats.exactIndexStorageHighWaterBytes(),
        Math.max(stateStats.updateScratchHighWaterBytes(),
            traceStats.updateScratchHighWaterBytes()),
        Math.max(stateStats.operationScratchHighWaterBytes(),
            traceStats.operationScratchHighWaterBytes()),
        stateStats.growthCount(),
        stateStats.capacity(), traceStats.capacity());
  }

  public SomaGroupMetadata runtimeMetadata() {
    return group.metadata();
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("simulation runtime is closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    Throwable failure = null;
    try {
      group.release();
    } catch (Throwable releaseFailure) {
      failure = releaseFailure;
    } finally {
      Arrays.fill(grass, 0.0);
      Arrays.fill(cellPopulation, 0);
      Arrays.fill(cellShare, 0.0);
      Arrays.fill(cellConsumption, 0.0);
    }
    if (failure instanceof RuntimeException) {
      throw (RuntimeException) failure;
    }
    if (failure instanceof Error) throw (Error) failure;
    if (failure != null) {
      throw new IllegalStateException("runtime release failed", failure);
    }
  }
}
