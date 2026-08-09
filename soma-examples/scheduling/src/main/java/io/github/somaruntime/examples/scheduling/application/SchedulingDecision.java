package io.github.somaruntime.examples.scheduling.application;

import java.util.Comparator;

public final class SchedulingDecision {
    public static final Comparator<SchedulingDecision> EARLIEST_COMPLETION =
            Comparator.comparingLong(SchedulingDecision::completionMinute)
                    .thenComparingLong(SchedulingDecision::machineId)
                    .thenComparingLong(SchedulingDecision::optionId);

    private final long jobId;
    private final long optionId;
    private final long machineId;
    private final long startMinute;
    private final long completionMinute;

    public SchedulingDecision(
            long jobId,
            long optionId,
            long machineId,
            long startMinute,
            long completionMinute) {
        this.jobId = jobId;
        this.optionId = optionId;
        this.machineId = machineId;
        this.startMinute = startMinute;
        this.completionMinute = completionMinute;
    }

    public long jobId() { return jobId; }
    public long optionId() { return optionId; }
    public long machineId() { return machineId; }
    public long startMinute() { return startMinute; }
    public long completionMinute() { return completionMinute; }
}
