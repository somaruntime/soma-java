package com.hgtech.soma.dataflow;

/** Detached invocation diagnostics; never a source of business facts. */
public final class DataFlowStats {
    private final String definitionIdentity;
    private final String templateIdentity;
    private final String policyIdentity;
    private final StatsMode statsMode;
    private final long boundSources;
    private final long scanned;
    private final long matched;
    private final long outputElements;
    private final int tasks;
    private final int workers;
    private final long elapsedNanos;
    private final String outcome;
    private final String failureCode;
    private final String failurePhase;
    private final long scratchBytes;
    private final long outputBytes;
    private final long maximumScratchBytes;
    private final long maximumOutputBytes;
    private final long maximumOutputElements;

    DataFlowStats(
            String definitionIdentity,
            String templateIdentity,
            String policyIdentity,
            StatsMode statsMode,
            long boundSources,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers,
            long elapsedNanos,
            String outcome,
            String failureCode,
            String failurePhase,
            long scratchBytes,
            long outputBytes,
            long maximumScratchBytes,
            long maximumOutputBytes,
            long maximumOutputElements) {
        this.definitionIdentity = definitionIdentity;
        this.templateIdentity = templateIdentity;
        this.policyIdentity = policyIdentity;
        this.statsMode = statsMode;
        this.boundSources = boundSources;
        this.scanned = scanned;
        this.matched = matched;
        this.outputElements = outputElements;
        this.tasks = tasks;
        this.workers = workers;
        this.elapsedNanos = elapsedNanos;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.failurePhase = failurePhase;
        this.scratchBytes = scratchBytes;
        this.outputBytes = outputBytes;
        this.maximumScratchBytes = maximumScratchBytes;
        this.maximumOutputBytes = maximumOutputBytes;
        this.maximumOutputElements = maximumOutputElements;
    }

    public String definitionIdentity() { return definitionIdentity; }
    public String templateIdentity() { return templateIdentity; }
    public String policyIdentity() { return policyIdentity; }
    public StatsMode statsMode() { return statsMode; }
    public long boundSources() { return boundSources; }
    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long outputElements() { return outputElements; }
    public int tasks() { return tasks; }
    public int workers() { return workers; }
    public long elapsedNanos() { return elapsedNanos; }
    public String outcome() { return outcome; }
    public String failureCode() { return failureCode; }
    public String failurePhase() { return failurePhase; }
    public long scratchBytes() { return scratchBytes; }
    public long outputBytes() { return outputBytes; }
    public long maximumScratchBytes() { return maximumScratchBytes; }
    public long maximumOutputBytes() { return maximumOutputBytes; }
    public long maximumOutputElements() { return maximumOutputElements; }
}
