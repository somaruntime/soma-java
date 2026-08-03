package io.github.somaruntime.examples.scheduling.domain;

/** Detached decision value; it never retains a SOMA View or Table storage. */
public final class SchedulingDecision {
    private final long jobId;
    private final int machineId;
    private final int priority;
    private final long processingMinutes;

    public SchedulingDecision(long jobId, int machineId, int priority,
            long processingMinutes) {
        this.jobId = jobId;
        this.machineId = machineId;
        this.priority = priority;
        this.processingMinutes = processingMinutes;
    }

    public long jobId() {
        return jobId;
    }

    public int machineId() {
        return machineId;
    }

    public int priority() {
        return priority;
    }

    public long processingMinutes() {
        return processingMinutes;
    }
}
