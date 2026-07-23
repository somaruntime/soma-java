package com.hgtech.soma.examples.grassing.runtime;

import com.hgtech.soma.examples.grassing.config.SimulationConfig;
import com.hgtech.soma.examples.grassing.schema.GrasserState;
import com.hgtech.soma.examples.grassing.schema.TraceSample;
import com.hgtech.soma.examples.grassing.schema.generated.GrasserStateTable;
import com.hgtech.soma.examples.grassing.schema.generated.TraceSampleTable;
import com.hgtech.soma.runtime.DoubleColumnView;
import com.hgtech.soma.runtime.IndexSnapshot;
import com.hgtech.soma.runtime.MaterializationBudget;
import com.hgtech.soma.runtime.TableStats;

import java.util.Arrays;
import java.util.List;

/** 一次 simulation session 的 SOMA tables、grass grid 与 scratch owner。 */
public final class SimulationRuntime implements AutoCloseable {
  final SimulationConfig config;
  final String inputChecksum;
  final GrasserStateTable grassers;
  final TraceSampleTable traces;
  final double[] grass;
  final int[] cellPopulation;
  final double[] cellShare;
  final double[] cellConsumption;
  private long nextId;
  private boolean closed;

  SimulationRuntime(SimulationConfig config, String inputChecksum,
                    GrasserStateTable grassers,
                    TraceSampleTable traces,
                    double[] grass) {
    this.config = config;
    this.inputChecksum = inputChecksum;
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

  public int population() {
    ensureOpen();
    return grassers.size();
  }

  public int traceCount() {
    ensureOpen();
    return traces.size();
  }

  public int keyCount() {
    ensureOpen();
    final int[] count = new int[1];
    grassers.keys().forEach(key ->
        count[0] = Math.addExact(count[0], 1));
    return count[0];
  }

  public double[] grassCopy() {
    ensureOpen();
    return Arrays.copyOf(grass, grass.length);
  }

  public List<GrasserState> materializeIndividuals() {
    ensureOpen();
    long rows = Math.max(1L, grassers.size());
    MaterializationBudget budget = MaterializationBudget.builder()
        .maximumRows(rows)
        .maximumLeafValues(Math.multiplyExact(rows, 8L))
        .maximumTableInstances(1L)
        .maximumOwnershipDepth(1)
        .maximumEstimatedAllocationBytes(Math.max(
            1024L * 1024L, Math.multiplyExact(rows, 128L)))
        .build();
    return grassers.fetchAll(budget);
  }

  public List<TraceSample> materializeTraces() {
    ensureOpen();
    long rows = Math.max(1L, traces.size());
    MaterializationBudget budget = MaterializationBudget.builder()
        .maximumRows(rows)
        .maximumLeafValues(Math.multiplyExact(rows, 10L))
        .maximumTableInstances(1L)
        .maximumOwnershipDepth(1)
        .maximumEstimatedAllocationBytes(Math.max(
            1024L * 1024L, Math.multiplyExact(rows, 128L)))
        .build();
    return traces.fetchAll(budget);
  }

  /**
   * 演示 caller-responsibility 的同步只读 IndexSnapshot gather。
   */
  public double snapshotEnergySum() {
    ensureOpen();
    IndexSnapshot snapshot = grassers.indexSnapshot();
    grassers.requireCurrent(snapshot);
    DoubleColumnView energy = grassers.energyColumn();
    try {
      double total = 0.0;
      for (int position = 0; position < snapshot.size(); position++) {
        total += energy.getDouble(snapshot.indexAt(position));
      }
      return total;
    } finally {
      energy.close();
    }
  }

  public String schemaHash() {
    ensureOpen();
    return grassers.runtimePlan().schemaHash();
  }

  public String runtimePlanHash() {
    ensureOpen();
    return grassers.runtimePlan().runtimePlanHash();
  }

  public RuntimeEvidence runtimeEvidence() {
    ensureOpen();
    TableStats stateStats = grassers.statsSnapshot();
    TableStats traceStats = traces.statsSnapshot();
    return new RuntimeEvidence(
        stateStats.keySpaceProbeCount(),
        stateStats.exactIndexProbeCount(),
        stateStats.exactIndexStorageHighWaterBytes(),
        Math.max(stateStats.updateScratchHighWaterBytes(),
            traceStats.updateScratchHighWaterBytes()),
        Math.max(stateStats.operationScratchHighWaterBytes(),
            traceStats.operationScratchHighWaterBytes()),
        stateStats.growthCount(),
        stateStats.capacity(), traceStats.capacity());
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("simulation runtime is closed");
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    traces.release();
    grassers.release();
    Arrays.fill(grass, 0.0);
    Arrays.fill(cellPopulation, 0);
    Arrays.fill(cellShare, 0.0);
    Arrays.fill(cellConsumption, 0.0);
  }

  public static final class RuntimeEvidence {
    public final long keySpaceProbes;
    public final long exactIndexProbes;
    public final long exactIndexHighWaterBytes;
    public final long updateScratchHighWaterBytes;
    public final long operationScratchHighWaterBytes;
    public final long populationGrowthCount;
    public final int populationCapacity;
    public final int traceCapacity;

    RuntimeEvidence(long keySpaceProbes,
                    long exactIndexProbes,
                    long exactIndexHighWaterBytes,
                    long updateScratchHighWaterBytes,
                    long operationScratchHighWaterBytes,
                    long populationGrowthCount,
                    int populationCapacity,
                    int traceCapacity) {
      this.keySpaceProbes = keySpaceProbes;
      this.exactIndexProbes = exactIndexProbes;
      this.exactIndexHighWaterBytes = exactIndexHighWaterBytes;
      this.updateScratchHighWaterBytes = updateScratchHighWaterBytes;
      this.operationScratchHighWaterBytes = operationScratchHighWaterBytes;
      this.populationGrowthCount = populationGrowthCount;
      this.populationCapacity = populationCapacity;
      this.traceCapacity = traceCapacity;
    }
  }
}
