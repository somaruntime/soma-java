package com.hgtech.soma.examples.grassing.result;

/** 与 live runtime 分离的轻量运行诊断。 */
public final class SimulationDiagnostics {
  private final String schemaHash;
  private final String runtimePlanHash;
  private final long exactIndexHighWaterBytes;
  private final long updateScratchHighWaterBytes;
  private final long operationScratchHighWaterBytes;
  private final long populationGrowthCount;
  private final int populationCapacity;
  private final int traceCapacity;

  public SimulationDiagnostics(
      String schemaHash, String runtimePlanHash,
      long exactIndexHighWaterBytes,
      long updateScratchHighWaterBytes,
      long operationScratchHighWaterBytes,
      long populationGrowthCount,
      int populationCapacity, int traceCapacity) {
    if (schemaHash == null) throw new NullPointerException("schemaHash");
    if (runtimePlanHash == null) {
      throw new NullPointerException("runtimePlanHash");
    }
    this.schemaHash = schemaHash;
    this.runtimePlanHash = runtimePlanHash;
    this.exactIndexHighWaterBytes = exactIndexHighWaterBytes;
    this.updateScratchHighWaterBytes = updateScratchHighWaterBytes;
    this.operationScratchHighWaterBytes = operationScratchHighWaterBytes;
    this.populationGrowthCount = populationGrowthCount;
    this.populationCapacity = populationCapacity;
    this.traceCapacity = traceCapacity;
  }

  public String schemaHash() { return schemaHash; }
  public String runtimePlanHash() { return runtimePlanHash; }
  public long exactIndexHighWaterBytes() {
    return exactIndexHighWaterBytes;
  }
  public long updateScratchHighWaterBytes() {
    return updateScratchHighWaterBytes;
  }
  public long operationScratchHighWaterBytes() {
    return operationScratchHighWaterBytes;
  }
  public long populationGrowthCount() { return populationGrowthCount; }
  public int populationCapacity() { return populationCapacity; }
  public int traceCapacity() { return traceCapacity; }
}
