package io.github.somaruntime.soma.dataflow;

/** Detached invocation diagnostics; never a source of business facts. */
public final class DataFlowStats {
    private final String definitionIdentity;
    private final String templateIdentity;
    private final String policyIdentity;
    private final StatsMode statsMode;
    private final DataFlowWorkStats work;
    private final DataFlowParallelStats parallel;
    private final DataFlowResourceStats resources;
    private final DataFlowDeliveryStats delivery;

    DataFlowStats(
            String definitionIdentity,
            String templateIdentity,
            String policyIdentity,
            StatsMode statsMode,
            long boundSources,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers,
            long elapsedNanos,
            String outcome,
            String failureCode,
            String failurePhase,
            String invocationLedgerIdentity,
            long sharedScratchCurrentBytes,
            long sharedScratchHighWaterBytes,
            long workerScratchCurrentBytes,
            long workerScratchHighWaterBytes,
            long outputCurrentBytes,
            long outputHighWaterBytes,
            long outputCurrentElements,
            long outputHighWaterElements,
            int taskCurrent,
            int taskHighWater,
            int workerCurrent,
            int workerHighWater,
            long maximumScratchBytes,
            long maximumOutputBytes,
            long maximumOutputElements,
            ResultDeliveryMode deliveryMode,
            long deliveredElements,
            boolean deliveryCompleted) {
        this.definitionIdentity = definitionIdentity;
        this.templateIdentity = templateIdentity;
        this.policyIdentity = policyIdentity;
        this.statsMode = statsMode;
        work = new DataFlowWorkStats(
                boundSources,
                scanned,
                matched,
                outputElements,
                elapsedNanos,
                outcome,
                failureCode,
                failurePhase);
        parallel = new DataFlowParallelStats(
                tasks,
                workers,
                taskCurrent,
                taskHighWater,
                workerCurrent,
                workerHighWater);
        resources = new DataFlowResourceStats(
                invocationLedgerIdentity,
                sharedScratchCurrentBytes,
                sharedScratchHighWaterBytes,
                workerScratchCurrentBytes,
                workerScratchHighWaterBytes,
                outputCurrentBytes,
                outputHighWaterBytes,
                outputCurrentElements,
                outputHighWaterElements,
                maximumScratchBytes,
                maximumOutputBytes,
                maximumOutputElements);
        delivery = new DataFlowDeliveryStats(
                deliveryMode,
                deliveredElements,
                deliveryCompleted);
    }

    public String definitionIdentity() { return definitionIdentity; }
    public String templateIdentity() { return templateIdentity; }
    public String policyIdentity() { return policyIdentity; }
    public StatsMode statsMode() { return statsMode; }
    public DataFlowWorkStats work() { return work; }
    public DataFlowParallelStats parallel() { return parallel; }
    public DataFlowResourceStats resources() { return resources; }
    public DataFlowDeliveryStats delivery() { return delivery; }
    public long boundSources() { return work.boundSources(); }
    public long scanned() { return work.scanned(); }
    public long matched() { return work.matched(); }
    public long outputElements() { return work.outputElements(); }
    public int tasks() { return parallel.tasks(); }
    public int workers() { return parallel.workers(); }
    public long elapsedNanos() { return work.elapsedNanos(); }
    public String outcome() { return work.outcome(); }
    public String failureCode() { return work.failureCode(); }
    public String failurePhase() { return work.failurePhase(); }
    public long scratchBytes() {
        return resources.scratchHighWaterBytes();
    }
    public long outputBytes() {
        return resources.outputHighWaterBytes();
    }
    public String invocationLedgerIdentity() {
        return resources.invocationLedgerIdentity();
    }
    public long sharedScratchCurrentBytes() {
        return resources.sharedScratchCurrentBytes();
    }
    public long sharedScratchHighWaterBytes() {
        return resources.sharedScratchHighWaterBytes();
    }
    public long workerScratchCurrentBytes() {
        return resources.workerScratchCurrentBytes();
    }
    public long workerScratchHighWaterBytes() {
        return resources.workerScratchHighWaterBytes();
    }
    public long outputCurrentBytes() {
        return resources.outputCurrentBytes();
    }
    public long outputHighWaterBytes() {
        return resources.outputHighWaterBytes();
    }
    public long outputCurrentElements() {
        return resources.outputCurrentElements();
    }
    public long outputHighWaterElements() {
        return resources.outputHighWaterElements();
    }
    public int taskCurrent() { return parallel.taskCurrent(); }
    public int taskHighWater() { return parallel.taskHighWater(); }
    public int workerCurrent() { return parallel.workerCurrent(); }
    public int workerHighWater() { return parallel.workerHighWater(); }
    public long maximumScratchBytes() {
        return resources.maximumScratchBytes();
    }
    public long maximumOutputBytes() {
        return resources.maximumOutputBytes();
    }
    public long maximumOutputElements() {
        return resources.maximumOutputElements();
    }
}
