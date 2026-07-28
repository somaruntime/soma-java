package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.runtime.SomaRuntimeException;
import io.github.somaruntime.soma.runtime.generated.RuntimeCompatibility;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.TreeMap;

/**
 * One-shot source binding and execution boundary.
 *
 * <p>An accepted execute call consumes the invocation regardless of outcome.
 * Bindings and live guards exist only for that execute call.</p>
 */
public final class DataFlowInvocation<R> {
    private enum State {
        NEW,
        BINDING,
        READY,
        RUNNING,
        COMMITTING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final DataFlowTemplate<R> template;
    private final DataFlowContext context;
    private final IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings =
            new IdentityHashMap<SourceSlot<?>, DataFlowBinding>();
    private final IdentityHashMap<ParameterSlot<?>, Object> parameters =
            new IdentityHashMap<ParameterSlot<?>, Object>();
    private ExecutionPolicy policy;
    private ExecutionBudget budget;
    private CancellationToken cancellationToken = CancellationToken.NONE;
    private State state = State.NEW;
    private DataFlowStats stats;

    DataFlowInvocation(DataFlowTemplate<R> template, DataFlowContext context) {
        if (template == null) {
            throw new NullPointerException("template");
        }
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.template = template;
        this.context = context;
        policy = context.defaultPolicy();
        budget = context.budgetUpperBound();
    }

    public <B extends DataFlowBinding> DataFlowInvocation<R> bind(
            SourceSlot<B> slot, B binding) {
        requireConfigurable("dataflow.bind");
        if (slot == null) {
            throw new NullPointerException("slot");
        }
        if (binding == null) {
            throw new NullPointerException("binding");
        }
        if (bindings.containsKey(slot)) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_duplicate_binding", slot.alias(), "dataflow.bind");
        }
        bindings.put(slot, binding);
        state = State.BINDING;
        return this;
    }

    public DataFlowInvocation<R> policy(ExecutionPolicy value) {
        requireConfigurable("dataflow.policy");
        policy = value == null ? context.defaultPolicy() : value;
        return this;
    }

    public <T> DataFlowInvocation<R> parameter(
            ParameterSlot<T> slot, T value) {
        requireConfigurable("dataflow.parameter");
        if (slot == null) {
            throw new NullPointerException("slot");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (!slot.type().isInstance(value)) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_parameter_type_mismatch",
                    slot.name(),
                    "dataflow.parameter");
        }
        if (parameters.containsKey(slot)) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_duplicate_parameter",
                    slot.name(),
                    "dataflow.parameter");
        }
        parameters.put(slot, value);
        state = State.BINDING;
        return this;
    }

    public DataFlowInvocation<R> budget(ExecutionBudget value) {
        requireConfigurable("dataflow.budget");
        if (value == null) {
            throw new NullPointerException("budget");
        }
        value.requireNarrowerThan(context.budgetUpperBound());
        budget = value;
        return this;
    }

    public DataFlowInvocation<R> cancellationToken(CancellationToken value) {
        requireConfigurable("dataflow.cancellation");
        cancellationToken = value == null ? CancellationToken.NONE : value;
        return this;
    }

    /**
     * Builds a detached, redacted bound explanation without consuming this
     * invocation.
     *
     * <p>The observed cardinality is valid only for the acquired read boundary
     * used by this call; it is not retained as planner state.</p>
     */
    public DataFlowExplain explain() {
        requireConfigurable("dataflow.explain");
        boolean contextBegun = false;
        List<DataFlowBinding> acquired =
                new ArrayList<DataFlowBinding>();
        Throwable primary = null;
        try {
            context.beginInvocation();
            contextBegun = true;
            checkDeadlineAndCancellation("dataflow.explain.preflight");
            TreeMap<Long, DataFlowBinding> canonical =
                    resolveAndValidate();
            for (DataFlowBinding binding : canonical.values()) {
                binding.acquire("dataflow.explain");
                acquired.add(binding);
            }
            long cardinality = 0L;
            for (SourceSlot<? extends DataFlowBinding> slot
                    : template.definition().requiredSources()) {
                int size = bindings.get(slot).packedSize();
                if (size < 0 || cardinality > Long.MAX_VALUE - size) {
                    throw DataFlowFailures.resource(
                            "dataflow_bound_cardinality_overflow",
                            slot.alias(),
                            "dataflow.explain",
                            "overflow");
                }
                cardinality += size;
            }
            String parallelDecision;
            String fallbackReason;
            if (policy.mode() == ExecutionPolicy.Mode.SEQUENTIAL) {
                parallelDecision = "sequential";
                fallbackReason = "policy-sequential";
            } else if (context.executor() == null
                    || context.workers() <= 1) {
                parallelDecision = "sequential";
                fallbackReason = "executor-unavailable";
            } else if (cardinality
                    < policy.minimumParallelCardinality()) {
                parallelDecision = "sequential";
                fallbackReason = "below-parallel-threshold";
            } else {
                parallelDecision = "adaptive-at-execute";
                fallbackReason =
                        "operator-properties-and-budget-evaluated-at-execute";
            }
            return new DataFlowExplain(
                    template.definition().identity(),
                    template.identity(),
                    template.definition().operation().logicalShape(),
                    template.definition().operation().logicalPlan(),
                    template.definition().operation().physicalPlan(),
                    true,
                    template.definition().requiredSources().size(),
                    cardinality,
                    parallelDecision,
                    fallbackReason,
                    parameterSummary(),
                    budgetSummary());
        } catch (RuntimeException failure) {
            primary = failure;
            throw failure;
        } catch (Error failure) {
            primary = failure;
            throw failure;
        } finally {
            SomaRuntimeException cleanup = releaseAcquired(
                    acquired,
                    true,
                    0L,
                    0L,
                    "",
                    "dataflow.explain");
            if (contextBegun) {
                try {
                    context.endInvocation();
                } catch (SomaRuntimeException failure) {
                    cleanup = append(cleanup, failure);
                }
            }
            if (cleanup != null) {
                if (primary != null) {
                    primary.addSuppressed(cleanup);
                } else {
                    throw cleanup;
                }
            }
        }
    }

    public R execute() {
        requireConfigurable("dataflow.execute");
        state = State.READY;
        long started = policy.statsMode() == StatsMode.OFF
                ? 0L : System.nanoTime();
        boolean contextBegun = false;
        List<DataFlowBinding> acquired = new ArrayList<DataFlowBinding>();
        long scanned = 0L;
        long matched = 0L;
        long output = 0L;
        int tasks = 0;
        int workers = 0;
        ResultDeliveryMode deliveryMode =
                template.definition().operation().resultDeliveryMode();
        long deliveredElements = 0L;
        boolean deliveryCompleted = false;
        String failureCode = "";
        String failurePhase = "";
        String currentPhase = "bind";
        Throwable primary = null;
        ExecutionFrame frame = null;
        try {
            context.beginInvocation();
            contextBegun = true;
            currentPhase = "preflight";
            checkDeadlineAndCancellation("dataflow.preflight");
            TreeMap<Long, DataFlowBinding> canonical = resolveAndValidate();
            for (DataFlowBinding binding : canonical.values()) {
                binding.acquire("dataflow.execute");
                acquired.add(binding);
            }
            state = State.RUNNING;
            currentPhase = "execute";
            checkDeadlineAndCancellation("dataflow.execute");
            frame = new ExecutionFrame(
                    context,
                    bindings,
                    parameters,
                    policy,
                    budget,
                    cancellationToken);
            ExecutionOutcome<R> outcome =
                    template.definition().operation().execute(frame);
            scanned = outcome.scanned;
            matched = outcome.matched;
            output = outcome.outputElements;
            tasks = outcome.tasks;
            workers = outcome.workers;
            R result = outcome.result;
            if (result instanceof DeliveryResult) {
                DeliveryResult delivered = (DeliveryResult) result;
                deliveredElements = delivered.deliveredElements();
                deliveryCompleted = delivered.completed();
            } else {
                deliveredElements = output;
                deliveryCompleted = true;
            }
            if (outcome.effect != null) {
                currentPhase = "effect-preflight";
                SomaRuntimeException releaseFailure =
                        releaseAcquired(
                                acquired,
                                false,
                                scanned,
                                matched,
                                "",
                                "dataflow.execute");
                acquired.clear();
                if (releaseFailure != null) {
                    throw releaseFailure;
                }
                checkDeadlineAndCancellation("dataflow.effect.preflight");
                state = State.COMMITTING;
                currentPhase = "effect";
                result = outcome.effect.commit();
            }
            state = State.COMPLETED;
            currentPhase = "cleanup";
            return result;
        } catch (SomaRuntimeException failure) {
            primary = failure;
            failureCode = failure.code();
            failurePhase = currentPhase;
            state = isCancellation(failureCode)
                    ? State.CANCELLED : State.FAILED;
            throw failure;
        } catch (RuntimeException failure) {
            state = State.FAILED;
            failureCode = "dataflow_unexpected_failure";
            failurePhase = currentPhase;
            SomaRuntimeException wrapped = DataFlowFailures.callback(
                    failureCode,
                    template.identity(),
                    "dataflow.execute",
                    failure);
            primary = wrapped;
            throw wrapped;
        } catch (Error failure) {
            primary = failure;
            state = State.FAILED;
            failureCode = "dataflow_error";
            failurePhase = currentPhase;
            throw failure;
        } finally {
            SomaRuntimeException cleanup = releaseAcquired(
                    acquired,
                    state == State.COMPLETED,
                    scanned,
                    matched,
                    failureCode,
                    "dataflow.execute");
            if (contextBegun) {
                try {
                    context.endInvocation();
                } catch (SomaRuntimeException failure) {
                    cleanup = append(cleanup, failure);
                }
            }
            if (frame != null) {
                try {
                    frame.closeLedger();
                } catch (SomaRuntimeException failure) {
                    cleanup = append(cleanup, failure);
                }
            }
            if (cleanup != null) {
                if (primary != null) {
                    primary.addSuppressed(cleanup);
                } else {
                    state = State.FAILED;
                    failureCode = cleanup.code();
                    failurePhase = "cleanup";
                }
            }
            stats = stats(
                    started,
                    frame,
                    scanned,
                    matched,
                    state == State.COMPLETED ? output : 0L,
                    state == State.COMPLETED ? "SUCCESS" : state.name(),
                    failureCode,
                    failurePhase,
                    tasks,
                    workers,
                    deliveryMode,
                    state == State.COMPLETED
                            ? deliveredElements : 0L,
                    state == State.COMPLETED
                            && deliveryCompleted);
            if (cleanup != null && primary == null) {
                throw cleanup;
            }
        }
    }

    private static SomaRuntimeException releaseAcquired(
            List<DataFlowBinding> acquired,
            boolean success,
            long scanned,
            long matched,
            String failureCode,
            String operation) {
        SomaRuntimeException cleanup = null;
        for (int index = acquired.size() - 1; index >= 0; index--) {
            try {
                acquired.get(index).release(
                        operation,
                        success,
                        scanned,
                        matched,
                        failureCode);
            } catch (SomaRuntimeException failure) {
                cleanup = append(cleanup, failure);
            }
        }
        return cleanup;
    }

    public DataFlowStats stats() {
        if (stats == null) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_stats_unavailable",
                    template.identity(),
                    "dataflow.stats",
                    state.name());
        }
        return stats;
    }

    public String state() {
        return state.name();
    }

    private TreeMap<Long, DataFlowBinding> resolveAndValidate() {
        List<SourceSlot<? extends DataFlowBinding>> required =
                template.definition().requiredSources();
        if (bindings.size() != required.size()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_binding_cardinality",
                    template.identity(),
                    "dataflow.bind");
        }
        List<ParameterSlot<?>> requiredParameters =
                template.definition().requiredParameters();
        if (parameters.size() != requiredParameters.size()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_parameter_cardinality",
                    template.identity(),
                    "dataflow.parameter");
        }
        for (ParameterSlot<?> slot : requiredParameters) {
            Object value = parameters.get(slot);
            if (value == null || !slot.type().isInstance(value)) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_missing_parameter",
                        slot.name(),
                        "dataflow.parameter");
            }
        }
        TreeMap<Long, DataFlowBinding> canonical =
                new TreeMap<Long, DataFlowBinding>();
        IdentityHashMap<Object, Long> physicalIds =
                new IdentityHashMap<Object, Long>();
        for (SourceSlot<? extends DataFlowBinding> slot : required) {
            DataFlowBinding binding = bindings.get(slot);
            if (binding == null) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_missing_binding", slot.alias(), "dataflow.bind");
            }
            verify(slot, binding);
            Long previousId = physicalIds.put(
                    binding.physicalIdentity(),
                    Long.valueOf(binding.aggregateInstanceId()));
            if (previousId != null
                    && previousId.longValue() != binding.aggregateInstanceId()) {
                throw DataFlowFailures.conflict(
                        "dataflow_aggregate_identity_mismatch",
                        slot.alias(),
                        "dataflow.bind",
                        Long.toString(binding.aggregateInstanceId()));
            }
            DataFlowBinding previous = canonical.put(
                    Long.valueOf(binding.aggregateInstanceId()), binding);
            if (previous != null
                    && previous.physicalIdentity() != binding.physicalIdentity()) {
                throw DataFlowFailures.conflict(
                        "dataflow_aggregate_identity_collision",
                        slot.alias(),
                        "dataflow.bind",
                        Long.toString(binding.aggregateInstanceId()));
            }
        }
        return canonical;
    }

    private static void verify(
            SourceSlot<? extends DataFlowBinding> slot, DataFlowBinding binding) {
        requireIdentity(
                slot.schemaIdentity(),
                binding.schemaIdentity(),
                "dataflow_schema_mismatch",
                slot.alias());
        requireIdentity(
                slot.tableIdentity(),
                binding.tableIdentity(),
                "dataflow_table_mismatch",
                slot.alias());
        requireIdentity(
                RuntimeCompatibility.GENERATED_PROTOCOL,
                binding.generatedProtocol(),
                "dataflow_generated_protocol_mismatch",
                slot.alias());
        requireIdentity(
                GeneratedDataFlow.TRANSFORMATION_PROTOCOL,
                binding.transformationProtocol(),
                "dataflow_transformation_protocol_mismatch",
                slot.alias());
        requireIdentity(
                GeneratedDataFlow.KERNEL_PROTOCOL,
                binding.kernelProtocol(),
                "dataflow_kernel_protocol_mismatch",
                slot.alias());
    }

    private static void requireIdentity(
            String expected, String actual, String code, String path) {
        if (!expected.equals(actual)) {
            throw DataFlowFailures.compatibility(
                    code, path, "dataflow.bind", expected, actual);
        }
    }

    private void checkDeadlineAndCancellation(String operation) {
        if (cancellationToken.isCancellationRequested()) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_cancelled",
                    template.identity(),
                    operation,
                    "CANCELLED");
        }
        long deadline = budget.deadlineNanos();
        if (deadline != 0L && System.nanoTime() - deadline >= 0L) {
            throw DataFlowFailures.resource(
                    "dataflow_deadline_exceeded",
                    template.identity(),
                    operation,
                    Long.toString(deadline));
        }
    }

    private DataFlowStats stats(
            long started,
            ExecutionFrame frame,
            long scanned,
            long matched,
            long output,
            String outcome,
            String failureCode,
            String failurePhase,
            int tasks,
            int workers,
            ResultDeliveryMode deliveryMode,
            long deliveredElements,
            boolean deliveryCompleted) {
        StatsMode mode = policy.statsMode();
        if (mode == StatsMode.OFF) {
            return null;
        }
        boolean detailed = mode == StatsMode.DETAILED;
        return new DataFlowStats(
                template.definition().identity(),
                template.identity(),
                policy.identity(),
                mode,
                bindings.size(),
                scanned,
                matched,
                output,
                detailed ? tasks : 0,
                detailed ? workers : 0,
                System.nanoTime() - started,
                outcome,
                failureCode,
                failurePhase,
                InvocationLedger.IDENTITY,
                frame == null ? 0L : frame.sharedScratchCurrentBytes(),
                frame == null ? 0L : frame.sharedScratchHighWaterBytes(),
                frame == null ? 0L : frame.workerScratchCurrentBytes(),
                frame == null ? 0L : frame.workerScratchHighWaterBytes(),
                frame == null ? 0L : frame.outputCurrentBytes(),
                frame == null ? 0L : frame.outputHighWaterBytes(),
                frame == null ? 0L : frame.outputCurrentElements(),
                frame == null ? 0L : frame.outputHighWaterElements(),
                frame == null ? 0 : frame.taskCurrent(),
                frame == null ? 0 : frame.taskHighWater(),
                frame == null ? 0 : frame.workerCurrent(),
                frame == null ? 0 : frame.workerHighWater(),
                budget.maximumInvocationScratchBytes(),
                budget.maximumOutputBytes(),
                budget.maximumOutputElements(),
                deliveryMode,
                deliveredElements,
                deliveryCompleted);
    }

    private String parameterSummary() {
        StringBuilder result = new StringBuilder();
        for (ParameterSlot<?> slot
                : template.definition().requiredParameters()) {
            if (result.length() != 0) {
                result.append(',');
            }
            result.append(slot.name())
                    .append(':')
                    .append(slot.type().getName())
                    .append("=<redacted>");
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private String budgetSummary() {
        return "outputElements<=" + budget.maximumOutputElements()
                + ",outputBytes<=" + budget.maximumOutputBytes()
                + ",scratchBytes<="
                + budget.maximumInvocationScratchBytes()
                + ",tasks<=" + budget.maximumTasks()
                + ",workers<=" + budget.maximumWorkers()
                + ",deadline=" + (budget.deadlineNanos() == 0L
                ? "none" : "configured");
    }

    private static boolean isCancellation(String code) {
        return "dataflow_cancelled".equals(code)
                || "dataflow_deadline_exceeded".equals(code);
    }

    private static SomaRuntimeException append(
            SomaRuntimeException primary, SomaRuntimeException next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private void requireConfigurable(String operation) {
        if (state != State.NEW && state != State.BINDING) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_invocation_consumed",
                    template.identity(),
                    operation,
                    state.name());
        }
    }
}
