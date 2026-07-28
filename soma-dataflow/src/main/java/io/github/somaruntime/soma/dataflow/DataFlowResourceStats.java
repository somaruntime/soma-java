package io.github.somaruntime.soma.dataflow;

/** Immutable Invocation resource-ledger and budget component. */
public final class DataFlowResourceStats {
    private final String invocationLedgerIdentity;
    private final long sharedScratchCurrentBytes;
    private final long sharedScratchHighWaterBytes;
    private final long workerScratchCurrentBytes;
    private final long workerScratchHighWaterBytes;
    private final long outputCurrentBytes;
    private final long outputHighWaterBytes;
    private final long outputCurrentElements;
    private final long outputHighWaterElements;
    private final long maximumScratchBytes;
    private final long maximumOutputBytes;
    private final long maximumOutputElements;

    DataFlowResourceStats(
            String invocationLedgerIdentity,
            long sharedScratchCurrentBytes,
            long sharedScratchHighWaterBytes,
            long workerScratchCurrentBytes,
            long workerScratchHighWaterBytes,
            long outputCurrentBytes,
            long outputHighWaterBytes,
            long outputCurrentElements,
            long outputHighWaterElements,
            long maximumScratchBytes,
            long maximumOutputBytes,
            long maximumOutputElements) {
        this.invocationLedgerIdentity = invocationLedgerIdentity;
        this.sharedScratchCurrentBytes = sharedScratchCurrentBytes;
        this.sharedScratchHighWaterBytes = sharedScratchHighWaterBytes;
        this.workerScratchCurrentBytes = workerScratchCurrentBytes;
        this.workerScratchHighWaterBytes = workerScratchHighWaterBytes;
        this.outputCurrentBytes = outputCurrentBytes;
        this.outputHighWaterBytes = outputHighWaterBytes;
        this.outputCurrentElements = outputCurrentElements;
        this.outputHighWaterElements = outputHighWaterElements;
        this.maximumScratchBytes = maximumScratchBytes;
        this.maximumOutputBytes = maximumOutputBytes;
        this.maximumOutputElements = maximumOutputElements;
    }

    public String invocationLedgerIdentity() {
        return invocationLedgerIdentity;
    }
    public long scratchHighWaterBytes() {
        return saturatedAdd(
                sharedScratchHighWaterBytes,
                workerScratchHighWaterBytes);
    }
    public long sharedScratchCurrentBytes() {
        return sharedScratchCurrentBytes;
    }
    public long sharedScratchHighWaterBytes() {
        return sharedScratchHighWaterBytes;
    }
    public long workerScratchCurrentBytes() {
        return workerScratchCurrentBytes;
    }
    public long workerScratchHighWaterBytes() {
        return workerScratchHighWaterBytes;
    }
    public long outputCurrentBytes() { return outputCurrentBytes; }
    public long outputHighWaterBytes() { return outputHighWaterBytes; }
    public long outputCurrentElements() { return outputCurrentElements; }
    public long outputHighWaterElements() {
        return outputHighWaterElements;
    }
    public long maximumScratchBytes() { return maximumScratchBytes; }
    public long maximumOutputBytes() { return maximumOutputBytes; }
    public long maximumOutputElements() { return maximumOutputElements; }

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : first + second;
    }
}
