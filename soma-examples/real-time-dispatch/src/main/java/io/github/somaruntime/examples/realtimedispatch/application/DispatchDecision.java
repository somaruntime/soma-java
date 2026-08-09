package io.github.somaruntime.examples.realtimedispatch.application;

import java.util.Comparator;

public final class DispatchDecision {
    public static final Comparator<DispatchDecision> BEST =
            Comparator.comparingLong(DispatchDecision::completionMinute)
                    .thenComparingInt(DispatchDecision::priority)
                    .thenComparingLong(DispatchDecision::machineId);

    private final long jobId;
    private final long machineId;
    private final long completionMinute;
    private final int priority;

    public DispatchDecision(long jobId, long machineId, long completionMinute, int priority) {
        this.jobId = jobId;
        this.machineId = machineId;
        this.completionMinute = completionMinute;
        this.priority = priority;
    }

    public long jobId() { return jobId; }
    public long machineId() { return machineId; }
    public long completionMinute() { return completionMinute; }
    public int priority() { return priority; }
}
