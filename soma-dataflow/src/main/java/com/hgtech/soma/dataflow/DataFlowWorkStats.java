package com.hgtech.soma.dataflow;

/** Immutable logical work and outcome component of one Invocation. */
public final class DataFlowWorkStats {
    private final long boundSources;
    private final long scanned;
    private final long matched;
    private final long outputElements;
    private final long elapsedNanos;
    private final String outcome;
    private final String failureCode;
    private final String failurePhase;

    DataFlowWorkStats(
            long boundSources,
            long scanned,
            long matched,
            long outputElements,
            long elapsedNanos,
            String outcome,
            String failureCode,
            String failurePhase) {
        this.boundSources = boundSources;
        this.scanned = scanned;
        this.matched = matched;
        this.outputElements = outputElements;
        this.elapsedNanos = elapsedNanos;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.failurePhase = failurePhase;
    }

    public long boundSources() { return boundSources; }
    public long scanned() { return scanned; }
    public long matched() { return matched; }
    public long outputElements() { return outputElements; }
    public long elapsedNanos() { return elapsedNanos; }
    public String outcome() { return outcome; }
    public String failureCode() { return failureCode; }
    public String failurePhase() { return failurePhase; }
}
