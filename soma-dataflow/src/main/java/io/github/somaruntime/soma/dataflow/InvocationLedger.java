package io.github.somaruntime.soma.dataflow;

/**
 * One-shot Invocation 的分相资源账本。
 *
 * <p>Shared scratch、worker scratch、output 与 parallel task/worker 分开计量。
 * current 在 phase/Invocation cleanup 后回落，high-water 在账本生命周期内单调。
 * 该账本只做本次 Invocation admission/observation，不重复计算 source retained
 * storage。</p>
 */
final class InvocationLedger {
    static final String IDENTITY = "soma-invocation-ledger-v1";

    private final ExecutionBudget budget;
    private long sharedScratchCurrentBytes;
    private long sharedScratchHighWaterBytes;
    private long workerScratchCurrentBytes;
    private long workerScratchHighWaterBytes;
    private long outputCurrentBytes;
    private long outputHighWaterBytes;
    private long outputCurrentElements;
    private long outputHighWaterElements;
    private int taskCurrent = 1;
    private int taskHighWater = 1;
    private int workerCurrent = 1;
    private int workerHighWater = 1;
    private boolean parallelActive;
    private boolean closed;

    InvocationLedger(ExecutionBudget budget) {
        if (budget == null) throw new NullPointerException("budget");
        this.budget = budget;
    }

    synchronized void reserveShared(long bytes, String operation) {
        requireOpen(operation);
        sharedScratchCurrentBytes = admittedBytes(
                sharedScratchCurrentBytes,
                bytes,
                budget.maximumInvocationScratchBytes(),
                "dataflow_scratch_budget_exceeded",
                "invocation.shared-scratch",
                operation);
        if (sharedScratchCurrentBytes > sharedScratchHighWaterBytes) {
            sharedScratchHighWaterBytes = sharedScratchCurrentBytes;
        }
    }

    synchronized void reserveOutput(
            long elements, long bytes, String operation) {
        requireOpen(operation);
        outputCurrentElements = admitted(
                outputCurrentElements,
                elements,
                budget.maximumOutputElements(),
                "dataflow_output_budget_exceeded",
                "invocation.output",
                operation);
        try {
            outputCurrentBytes = admittedBytes(
                    outputCurrentBytes,
                    bytes,
                    budget.maximumOutputBytes(),
                    "dataflow_output_budget_exceeded",
                    "invocation.output",
                    operation);
        } catch (RuntimeException failure) {
            outputCurrentElements -= elements;
            throw failure;
        }
        if (outputCurrentElements > outputHighWaterElements) {
            outputHighWaterElements = outputCurrentElements;
        }
        if (outputCurrentBytes > outputHighWaterBytes) {
            outputHighWaterBytes = outputCurrentBytes;
        }
    }

    synchronized void preflightDelivery(
            long elements, long bytes, String operation) {
        requireOpen(operation);
        admitted(
                0L,
                elements,
                budget.maximumOutputElements(),
                "dataflow_output_budget_exceeded",
                "invocation.delivery",
                operation);
        admittedBytes(
                0L,
                bytes,
                budget.maximumOutputBytes(),
                "dataflow_output_budget_exceeded",
                "invocation.delivery",
                operation);
    }

    synchronized InvocationPhaseLease beginParallel(
            int tasks,
            int workers,
            long perWorkerScratchBytes,
            String operation) {
        requireOpen(operation);
        if (parallelActive || tasks <= 1 || workers <= 1
                || workers > tasks
                || tasks > budget.maximumTasks()
                || workers > budget.maximumWorkers()
                || perWorkerScratchBytes < 0L
                || perWorkerScratchBytes
                > budget.maximumWorkerScratchBytes()) {
            throw DataFlowFailures.resource(
                    "dataflow_parallel_budget_exceeded",
                    "invocation.parallel",
                    operation,
                    tasks + "/" + workers + "/" + perWorkerScratchBytes);
        }
        long workerBytes = multiply(
                workers, perWorkerScratchBytes, operation);
        parallelActive = true;
        taskCurrent = tasks;
        workerCurrent = workers;
        workerScratchCurrentBytes = workerBytes;
        if (taskCurrent > taskHighWater) taskHighWater = taskCurrent;
        if (workerCurrent > workerHighWater) workerHighWater = workerCurrent;
        if (workerBytes > workerScratchHighWaterBytes) {
            workerScratchHighWaterBytes = workerBytes;
        }
        return new InvocationPhaseLease(this);
    }

    synchronized void endParallel() {
        if (!parallelActive) {
            throw DataFlowFailures.internal(
                    "dataflow_invocation_lease_underflow",
                    "invocation.parallel",
                    "dataflow.parallel.cleanup",
                    IDENTITY);
        }
        parallelActive = false;
        taskCurrent = 1;
        workerCurrent = 1;
        workerScratchCurrentBytes = 0L;
    }

    synchronized void close() {
        if (closed) return;
        if (parallelActive) {
            throw DataFlowFailures.internal(
                    "dataflow_invocation_lease_leak",
                    "invocation.parallel",
                    "dataflow.cleanup",
                    IDENTITY);
        }
        sharedScratchCurrentBytes = 0L;
        workerScratchCurrentBytes = 0L;
        outputCurrentBytes = 0L;
        outputCurrentElements = 0L;
        taskCurrent = 0;
        workerCurrent = 0;
        closed = true;
    }

    synchronized long sharedScratchCurrentBytes() {
        return sharedScratchCurrentBytes;
    }

    synchronized long sharedScratchHighWaterBytes() {
        return sharedScratchHighWaterBytes;
    }

    synchronized long workerScratchCurrentBytes() {
        return workerScratchCurrentBytes;
    }

    synchronized long workerScratchHighWaterBytes() {
        return workerScratchHighWaterBytes;
    }

    synchronized long outputCurrentBytes() {
        return outputCurrentBytes;
    }

    synchronized long outputHighWaterBytes() {
        return outputHighWaterBytes;
    }

    synchronized long outputCurrentElements() {
        return outputCurrentElements;
    }

    synchronized long outputHighWaterElements() {
        return outputHighWaterElements;
    }

    synchronized int taskCurrent() {
        return taskCurrent;
    }

    synchronized int taskHighWater() {
        return taskHighWater;
    }

    synchronized int workerCurrent() {
        return workerCurrent;
    }

    synchronized int workerHighWater() {
        return workerHighWater;
    }

    private void requireOpen(String operation) {
        if (closed) {
            throw DataFlowFailures.internal(
                    "dataflow_invocation_ledger_closed",
                    "invocation",
                    operation,
                    IDENTITY);
        }
    }

    private static long admittedBytes(
            long current,
            long delta,
            long maximum,
            String code,
            String path,
            String operation) {
        return admitted(current, delta, maximum, code, path, operation);
    }

    private static long admitted(
            long current,
            long delta,
            long maximum,
            String code,
            String path,
            String operation) {
        if (delta < 0L || current > Long.MAX_VALUE - delta) {
            throw DataFlowFailures.resource(
                    code, path, operation, "overflow");
        }
        long next = current + delta;
        if (next > maximum) {
            throw DataFlowFailures.resource(
                    code, path, operation, Long.toString(next));
        }
        return next;
    }

    private static long multiply(
            int count, long width, String operation) {
        if (count < 0 || width < 0L
                || (width != 0L
                && (long) count > Long.MAX_VALUE / width)) {
            throw DataFlowFailures.resource(
                    "dataflow_scratch_budget_exceeded",
                    "invocation.worker-scratch",
                    operation,
                    "overflow");
        }
        return (long) count * width;
    }
}

final class InvocationPhaseLease implements AutoCloseable {
    private InvocationLedger owner;

    InvocationPhaseLease(InvocationLedger owner) {
        this.owner = owner;
    }

    @Override
    public void close() {
        InvocationLedger current = owner;
        if (current == null) return;
        owner = null;
        current.endParallel();
    }
}
