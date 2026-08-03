package io.github.somaruntime.examples.dispatch.domain;

/** Detached application decision produced after explicit candidate ordering. */
public final class DispatchDecision {
    private final long requestId;
    private final int machineId;
    private final int priority;
    private final long processingMinutes;

    public DispatchDecision(long requestId, int machineId, int priority,
            long processingMinutes) {
        this.requestId = requestId;
        this.machineId = machineId;
        this.priority = priority;
        this.processingMinutes = processingMinutes;
    }

    public long requestId() {
        return requestId;
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
