package io.github.somaruntime.examples.scheduling.solver.api;

/** Detached, immutable query result for one scheduled operation. */
public final class OperationResult {
    private final long operationId;
    private final long jobId;
    private final long sequence;
    private final long readyTime;
    private final long machineId;
    private final long processingTime;
    private final long startTime;
    private final long completionTime;

    public OperationResult(
            long operationId,
            long jobId,
            long sequence,
            long readyTime,
            long machineId,
            long processingTime,
            long startTime,
            long completionTime) {
        this.operationId = operationId;
        this.jobId = jobId;
        this.sequence = sequence;
        this.readyTime = readyTime;
        this.machineId = machineId;
        this.processingTime = processingTime;
        this.startTime = startTime;
        this.completionTime = completionTime;
    }

    public long operationId() { return operationId; }
    public long jobId() { return jobId; }
    public long sequence() { return sequence; }
    public long readyTime() { return readyTime; }
    public long machineId() { return machineId; }
    public long processingTime() { return processingTime; }
    public long startTime() { return startTime; }
    public long completionTime() { return completionTime; }
}
