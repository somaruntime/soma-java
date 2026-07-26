package com.hgtech.soma.dataflow;

/** Detached invocation diagnostics; never a source of business facts. */
public final class DataFlowStats {
    private final String definitionIdentity;
    private final String templateIdentity;
    private final String policyIdentity;
    private final long boundSources;
    private final long scanned;
    private final long matched;
    private final long outputElements;
    private final int tasks;
    private final int workers;
    private final long elapsedNanos;
    private final String outcome;
    private final String failureCode;

    DataFlowStats(
            String definitionIdentity,
            String templateIdentity,
            String policyIdentity,
            long boundSources,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers,
            long elapsedNanos,
            String outcome,
            String failureCode) {
        this.definitionIdentity = definitionIdentity;
        this.templateIdentity = templateIdentity;
        this.policyIdentity = policyIdentity;
        this.boundSources = boundSources;
        this.scanned = scanned;
        this.matched = matched;
        this.outputElements = outputElements;
        this.tasks = tasks;
        this.workers = workers;
        this.elapsedNanos = elapsedNanos;
        this.outcome = outcome;
        this.failureCode = failureCode;
    }

    public String definitionIdentity() { return definitionIdentity; }
    public String templateIdentity() { return templateIdentity; }
    public String policyIdentity() { return policyIdentity; }
    public long boundSources() { return boundSources; }
    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long outputElements() { return outputElements; }
    public int tasks() { return tasks; }
    public int workers() { return workers; }
    public long elapsedNanos() { return elapsedNanos; }
    public String outcome() { return outcome; }
    public String failureCode() { return failureCode; }
}
