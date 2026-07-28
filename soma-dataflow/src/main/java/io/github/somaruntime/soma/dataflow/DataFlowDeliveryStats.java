package io.github.somaruntime.soma.dataflow;

/** Immutable result-delivery component of one Invocation. */
public final class DataFlowDeliveryStats {
    private final ResultDeliveryMode mode;
    private final long deliveredElements;
    private final boolean completed;

    DataFlowDeliveryStats(
            ResultDeliveryMode mode,
            long deliveredElements,
            boolean completed) {
        this.mode = mode;
        this.deliveredElements = deliveredElements;
        this.completed = completed;
    }

    public ResultDeliveryMode mode() { return mode; }
    public long deliveredElements() { return deliveredElements; }
    public boolean completed() { return completed; }
}
