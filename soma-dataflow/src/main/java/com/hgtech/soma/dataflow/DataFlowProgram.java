package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Package-private logical-program and execution contracts. */
interface DataFlowOperation<R> {
    List<SourceSlot<? extends DataFlowBinding>> requiredSources();

    String canonicalForm();

    String logicalShape();

    String logicalPlan();

    String physicalPlan();

    ExecutionOutcome<R> execute(ExecutionFrame frame);
}

interface DeferredEffect<R> {
    R commit();
}

final class ExecutionOutcome<R> {
    final R result;
    final DeferredEffect<R> effect;
    final long scanned;
    final long matched;
    final long outputElements;
    final int tasks;
    final int workers;

    ExecutionOutcome(
            R result,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers) {
        this.result = result;
        this.effect = null;
        this.scanned = scanned;
        this.matched = matched;
        this.outputElements = outputElements;
        this.tasks = tasks;
        this.workers = workers;
    }

    private ExecutionOutcome(
            DeferredEffect<R> effect,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers) {
        this.result = null;
        this.effect = effect;
        this.scanned = scanned;
        this.matched = matched;
        this.outputElements = outputElements;
        this.tasks = tasks;
        this.workers = workers;
    }

    static <R> ExecutionOutcome<R> effect(
            DeferredEffect<R> effect,
            long scanned,
            long matched,
            long outputElements) {
        if (effect == null) {
            throw new NullPointerException("effect");
        }
        return new ExecutionOutcome<R>(
                effect, scanned, matched, outputElements, 1, 1);
    }

    static <R> ExecutionOutcome<R> effect(
            DeferredEffect<R> effect,
            long scanned,
            long matched,
            long outputElements,
            int tasks,
            int workers) {
        if (effect == null) {
            throw new NullPointerException("effect");
        }
        return new ExecutionOutcome<R>(
                effect, scanned, matched, outputElements, tasks, workers);
    }
}

final class ExecutionFrame {
    private final DataFlowContext context;
    private final IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings;
    private final ExecutionPolicy policy;
    private final ExecutionBudget budget;
    private final CancellationToken cancellationToken;
    private long scratchBytes;
    private long outputBytes;
    private long outputElements;

    ExecutionFrame(
            DataFlowContext context,
            IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings,
            ExecutionPolicy policy,
            ExecutionBudget budget,
            CancellationToken cancellationToken) {
        this.context = context;
        this.bindings = bindings;
        this.policy = policy;
        this.budget = budget;
        this.cancellationToken = cancellationToken;
    }

    DataFlowBinding binding(SourceSlot<?> source) {
        DataFlowBinding result = bindings.get(source);
        if (result == null) {
            throw DataFlowFailures.internal(
                    "dataflow_binding_missing_after_preflight",
                    source.alias(),
                    "dataflow.execute",
                    source.tableIdentity());
        }
        return result;
    }

    ExecutionPolicy policy() {
        return policy;
    }

    DataFlowContext context() {
        return context;
    }

    ExecutionBudget budget() {
        return budget;
    }

    void checkBoundary(String operation) {
        if (Thread.currentThread().isInterrupted()) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_parallel_interrupted",
                    "invocation",
                    operation,
                    "INTERRUPTED");
        }
        if (cancellationToken.isCancellationRequested()) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_cancelled", "invocation", operation, "CANCELLED");
        }
        long deadline = budget.deadlineNanos();
        if (deadline != 0L && System.nanoTime() - deadline >= 0L) {
            throw DataFlowFailures.resource(
                    "dataflow_deadline_exceeded",
                    "invocation",
                    operation,
                    Long.toString(deadline));
        }
    }

    int[] newScratchIndexes(int length, String operation) {
        long bytes = multiply(length, 4L, operation);
        reserveScratch(bytes, operation);
        return new int[length];
    }

    long[] newScratchLongs(int length, String operation) {
        long bytes = multiply(length, 8L, operation);
        reserveScratch(bytes, operation);
        return new long[length];
    }

    double[] newScratchDoubles(int length, String operation) {
        long bytes = multiply(length, 8L, operation);
        reserveScratch(bytes, operation);
        return new double[length];
    }

    int[] newOutputIndexes(int length, String operation) {
        reserveOutput(length, multiply(length, 4L, operation), operation);
        return new int[length];
    }

    long[] newOutputLongs(int length, String operation) {
        reserveOutput(length, multiply(length, 8L, operation), operation);
        return new long[length];
    }

    double[] newOutputDoubles(int length, String operation) {
        reserveOutput(length, multiply(length, 8L, operation), operation);
        return new double[length];
    }

    Object[] newOutputObjects(int length, String operation) {
        reserveOutput(length, multiply(length, 8L, operation), operation);
        return new Object[length];
    }

    boolean[] newOutputBooleans(int length, String operation) {
        reserveOutput(length, length, operation);
        return new boolean[length];
    }

    void reserveOutput(long elements, long bytes, String operation) {
        if (elements < 0L || bytes < 0L
                || outputElements > Long.MAX_VALUE - elements
                || outputBytes > Long.MAX_VALUE - bytes) {
            throw DataFlowFailures.resource(
                    "dataflow_output_budget_exceeded",
                    "invocation.output",
                    operation,
                    "overflow");
        }
        long nextElements = outputElements + elements;
        long nextBytes = outputBytes + bytes;
        if (nextElements > budget.maximumOutputElements()
                || nextBytes > budget.maximumOutputBytes()) {
            throw DataFlowFailures.resource(
                    "dataflow_output_budget_exceeded",
                    "invocation.output",
                    operation,
                    nextElements + "/" + nextBytes);
        }
        outputElements = nextElements;
        outputBytes = nextBytes;
    }

    private void reserveScratch(long bytes, String operation) {
        if (bytes < 0L || scratchBytes > Long.MAX_VALUE - bytes) {
            throw DataFlowFailures.resource(
                    "dataflow_scratch_budget_exceeded",
                    "invocation.scratch",
                    operation,
                    "overflow");
        }
        long next = scratchBytes + bytes;
        if (next > budget.maximumInvocationScratchBytes()) {
            throw DataFlowFailures.resource(
                    "dataflow_scratch_budget_exceeded",
                    "invocation.scratch",
                    operation,
                    Long.toString(next));
        }
        scratchBytes = next;
    }

    private static long multiply(int count, long width, String operation) {
        if (count < 0 || (long) count > Long.MAX_VALUE / width) {
            throw DataFlowFailures.resource(
                    "dataflow_size_overflow",
                    "invocation",
                    operation,
                    Integer.toString(count));
        }
        return (long) count * width;
    }
}

abstract class SingleSourceOperation<R> implements DataFlowOperation<R> {
    final SourceSlot<? extends DataFlowBinding> source;
    private final List<SourceSlot<? extends DataFlowBinding>> requiredSources;

    SingleSourceOperation(SourceSlot<? extends DataFlowBinding> source) {
        this.source = source;
        requiredSources = Collections
                .<SourceSlot<? extends DataFlowBinding>>singletonList(source);
    }

    @Override
    public final List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return requiredSources;
    }
}

abstract class MultiSourceOperation<R> implements DataFlowOperation<R> {
    private final List<SourceSlot<? extends DataFlowBinding>> requiredSources;

    MultiSourceOperation(SourceSlot<?> first, SourceSlot<?> second) {
        ArrayList<SourceSlot<? extends DataFlowBinding>> sources =
                new ArrayList<SourceSlot<? extends DataFlowBinding>>(2);
        @SuppressWarnings("unchecked")
        SourceSlot<? extends DataFlowBinding> left =
                (SourceSlot<? extends DataFlowBinding>) first;
        @SuppressWarnings("unchecked")
        SourceSlot<? extends DataFlowBinding> right =
                (SourceSlot<? extends DataFlowBinding>) second;
        sources.add(left);
        if (right != left) {
            sources.add(right);
        }
        requiredSources = Collections.unmodifiableList(sources);
    }

    @Override
    public final List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return requiredSources;
    }
}
