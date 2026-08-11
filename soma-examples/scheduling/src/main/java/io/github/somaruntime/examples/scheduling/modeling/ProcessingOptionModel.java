package io.github.somaruntime.examples.scheduling.modeling;

/** Immutable Operation-to-Machine processing relation. */
public final class ProcessingOptionModel {
    private final long optionId;
    private final long machineId;
    private final long processingTime;

    public ProcessingOptionModel(long optionId, long machineId, long processingTime) {
        this.optionId = optionId;
        this.machineId = machineId;
        this.processingTime = processingTime;
    }

    public long optionId() { return optionId; }
    public long machineId() { return machineId; }
    public long processingTime() { return processingTime; }
}
