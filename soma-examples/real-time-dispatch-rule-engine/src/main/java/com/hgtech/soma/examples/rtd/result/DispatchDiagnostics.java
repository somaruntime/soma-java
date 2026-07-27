package com.hgtech.soma.examples.rtd.result;

/** Detached execution diagnostics；不参与领域 Result identity。 */
public final class DispatchDiagnostics {
  private final String definitionIdentity;
  private final String templateIdentity;
  private final String schemaHash;
  private final String runtimePlanHash;
  private final int invocations;
  private final long readyCandidates;
  private final long joinedPairs;
  private final long demandGroups;
  private final String demandChecksum;
  private final long admittedProcessingMinutes;
  private final long tasks;
  private final int maximumWorkers;

  public DispatchDiagnostics(
      String definitionIdentity,
      String templateIdentity,
      String schemaHash,
      String runtimePlanHash,
      int invocations,
      long readyCandidates,
      long joinedPairs,
      long demandGroups,
      String demandChecksum,
      long admittedProcessingMinutes,
      long tasks,
      int maximumWorkers) {
    if (definitionIdentity == null || templateIdentity == null
        || schemaHash == null || runtimePlanHash == null
        || demandChecksum == null) {
      throw new NullPointerException("diagnostic identity");
    }
    if (invocations < 0 || readyCandidates < 0L
        || joinedPairs < 0L || demandGroups < 0L
        || admittedProcessingMinutes < 0L
        || tasks < 0L || maximumWorkers < 0) {
      throw new IllegalArgumentException("invalid diagnostics");
    }
    this.definitionIdentity = definitionIdentity;
    this.templateIdentity = templateIdentity;
    this.schemaHash = schemaHash;
    this.runtimePlanHash = runtimePlanHash;
    this.invocations = invocations;
    this.readyCandidates = readyCandidates;
    this.joinedPairs = joinedPairs;
    this.demandGroups = demandGroups;
    this.demandChecksum = demandChecksum;
    this.admittedProcessingMinutes = admittedProcessingMinutes;
    this.tasks = tasks;
    this.maximumWorkers = maximumWorkers;
  }

  public String definitionIdentity() { return definitionIdentity; }
  public String templateIdentity() { return templateIdentity; }
  public String schemaHash() { return schemaHash; }
  public String runtimePlanHash() { return runtimePlanHash; }
  public int invocations() { return invocations; }
  public long readyCandidates() { return readyCandidates; }
  public long joinedPairs() { return joinedPairs; }
  public long demandGroups() { return demandGroups; }
  public String demandChecksum() { return demandChecksum; }
  public long admittedProcessingMinutes() {
    return admittedProcessingMinutes;
  }
  public long tasks() { return tasks; }
  public int maximumWorkers() { return maximumWorkers; }
}
