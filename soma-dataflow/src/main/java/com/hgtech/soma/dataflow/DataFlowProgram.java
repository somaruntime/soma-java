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

    default List<ParameterSlot<?>> requiredParameters() {
        return Collections.emptyList();
    }

    default boolean effectful() {
        return false;
    }

    default boolean parallelBranchSafe() {
        return false;
    }

    default ResultDeliveryMode resultDeliveryMode() {
        return ResultDeliveryMode.EAGER_DETACHED;
    }
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
    private final IdentityHashMap<ParameterSlot<?>, Object> parameters;
    private final ExecutionPolicy policy;
    private final ExecutionBudget budget;
    private final CancellationToken cancellationToken;
    private final InvocationLedger ledger;

    ExecutionFrame(
            DataFlowContext context,
            IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings,
            IdentityHashMap<ParameterSlot<?>, Object> parameters,
            ExecutionPolicy policy,
            ExecutionBudget budget,
            CancellationToken cancellationToken) {
        this(
                context,
                bindings,
                parameters,
                policy,
                budget,
                cancellationToken,
                new InvocationLedger(budget));
    }

    private ExecutionFrame(
            DataFlowContext context,
            IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings,
            IdentityHashMap<ParameterSlot<?>, Object> parameters,
            ExecutionPolicy policy,
            ExecutionBudget budget,
            CancellationToken cancellationToken,
            InvocationLedger ledger) {
        this.context = context;
        this.bindings = bindings;
        this.parameters = parameters;
        this.policy = policy;
        this.budget = budget;
        this.cancellationToken = cancellationToken;
        this.ledger = ledger;
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

    <T> T parameter(ParameterSlot<T> slot) {
        Object value = parameters.get(slot);
        if (value == null && !parameters.containsKey(slot)) {
            throw DataFlowFailures.internal(
                    "dataflow_parameter_missing_after_preflight",
                    slot.name(),
                    "dataflow.execute",
                    slot.type().getName());
        }
        return slot.type().cast(value);
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

    long scratchBytes() {
        return saturatedAdd(
                ledger.sharedScratchHighWaterBytes(),
                ledger.workerScratchHighWaterBytes());
    }

    long outputBytes() {
        return ledger.outputHighWaterBytes();
    }

    long outputElements() {
        return ledger.outputHighWaterElements();
    }

    ExecutionFrame sequentialChild() {
        return new ExecutionFrame(
                context,
                bindings,
                parameters,
                ExecutionPolicy.sequential()
                        .withStatsMode(policy.statsMode()),
                budget,
                cancellationToken,
                ledger);
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

    boolean[] newScratchBooleans(int length, String operation) {
        reserveScratch(length, operation);
        return new boolean[length];
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

    String[] newOutputStrings(int length, String operation) {
        reserveOutput(length, multiply(length, 8L, operation), operation);
        return new String[length];
    }

    boolean[] newOutputBooleans(int length, String operation) {
        reserveOutput(length, length, operation);
        return new boolean[length];
    }

    void reserveOutput(long elements, long bytes, String operation) {
        ledger.reserveOutput(elements, bytes, operation);
    }

    void preflightDelivery(long elements, long bytes, String operation) {
        ledger.preflightDelivery(elements, bytes, operation);
    }

    InvocationPhaseLease beginParallel(
            int tasks,
            int workers,
            long perWorkerScratchBytes,
            String operation) {
        return ledger.beginParallel(
                tasks, workers, perWorkerScratchBytes, operation);
    }

    void closeLedger() {
        ledger.close();
    }

    long sharedScratchCurrentBytes() {
        return ledger.sharedScratchCurrentBytes();
    }

    long sharedScratchHighWaterBytes() {
        return ledger.sharedScratchHighWaterBytes();
    }

    long workerScratchCurrentBytes() {
        return ledger.workerScratchCurrentBytes();
    }

    long workerScratchHighWaterBytes() {
        return ledger.workerScratchHighWaterBytes();
    }

    long outputCurrentBytes() {
        return ledger.outputCurrentBytes();
    }

    long outputHighWaterBytes() {
        return ledger.outputHighWaterBytes();
    }

    long outputCurrentElements() {
        return ledger.outputCurrentElements();
    }

    long outputHighWaterElements() {
        return ledger.outputHighWaterElements();
    }

    int taskCurrent() {
        return ledger.taskCurrent();
    }

    int taskHighWater() {
        return ledger.taskHighWater();
    }

    int workerCurrent() {
        return ledger.workerCurrent();
    }

    int workerHighWater() {
        return ledger.workerHighWater();
    }

    private void reserveScratch(long bytes, String operation) {
        ledger.reserveShared(bytes, operation);
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

    private static long saturatedAdd(long first, long second) {
        return first > Long.MAX_VALUE - second
                ? Long.MAX_VALUE : first + second;
    }
}

abstract class SingleSourceOperation<R> implements DataFlowOperation<R> {
    final SourceSlot<? extends DataFlowBinding> source;
    private final List<SourceSlot<? extends DataFlowBinding>> requiredSources;
    private final List<ParameterSlot<?>> requiredParameters;

    SingleSourceOperation(SourceSlot<? extends DataFlowBinding> source) {
        this(source, Collections.<ParameterSlot<?>>emptyList());
    }

    SingleSourceOperation(CandidateProgram<?> program) {
        this(program.source(), program.requiredParameters());
    }

    SingleSourceOperation(
            CandidateProgram<?> program,
            List<ParameterSlot<?>> additionalParameters) {
        this(
                program.source(),
                DataFlowSupport.unionParameters(
                        program.requiredParameters(), additionalParameters));
    }

    SingleSourceOperation(
            SourceSlot<? extends DataFlowBinding> source,
            List<ParameterSlot<?>> requiredParameters) {
        this.source = source;
        requiredSources = Collections
                .<SourceSlot<? extends DataFlowBinding>>singletonList(source);
        this.requiredParameters = requiredParameters;
    }

    @Override
    public final List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return requiredSources;
    }

    @Override
    public final List<ParameterSlot<?>> requiredParameters() {
        return requiredParameters;
    }
}

abstract class MultiSourceOperation<R> implements DataFlowOperation<R> {
    private final List<SourceSlot<? extends DataFlowBinding>> requiredSources;
    private final List<ParameterSlot<?>> requiredParameters;

    MultiSourceOperation(SourceSlot<?> first, SourceSlot<?> second) {
        this(first, second, Collections.<ParameterSlot<?>>emptyList());
    }

    MultiSourceOperation(
            CandidateProgram<?> first, CandidateProgram<?> second) {
        this(
                first,
                second,
                Collections.<ParameterSlot<?>>emptyList());
    }

    MultiSourceOperation(
            CandidateProgram<?> first,
            CandidateProgram<?> second,
            List<ParameterSlot<?>> additionalParameters) {
        this(
                first.source(),
                second.source(),
                DataFlowSupport.unionParameters(
                        DataFlowSupport.unionParameters(
                                first.requiredParameters(),
                                second.requiredParameters()),
                        additionalParameters));
    }

    private MultiSourceOperation(
            SourceSlot<?> first,
            SourceSlot<?> second,
            List<ParameterSlot<?>> requiredParameters) {
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
        this.requiredParameters = requiredParameters;
    }

    @Override
    public final List<SourceSlot<? extends DataFlowBinding>> requiredSources() {
        return requiredSources;
    }

    @Override
    public final List<ParameterSlot<?>> requiredParameters() {
        return requiredParameters;
    }
}
