package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.runtime.SomaRuntimeException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One-shot source binding and execution boundary.
 *
 * <p>An accepted execute call consumes the invocation regardless of outcome.</p>
 */
public final class DataFlowInvocation<R> {
    private enum State {
        NEW,
        BINDING,
        READY,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final DataFlowTemplate<R> template;
    private final DataFlowContext context;
    private final IdentityHashMap<SourceSlot<?>, DataFlowBinding> bindings =
            new IdentityHashMap<SourceSlot<?>, DataFlowBinding>();
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
        if (bindings.put(slot, binding) != null) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_duplicate_binding", slot.alias(), "dataflow.bind");
        }
        state = State.BINDING;
        return this;
    }

    public DataFlowInvocation<R> policy(ExecutionPolicy value) {
        requireConfigurable("dataflow.policy");
        policy = value == null
                ? context.defaultPolicy() : value;
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

    public R execute() {
        requireConfigurable("dataflow.execute");
        state = State.READY;
        long started = System.nanoTime();
        context.beginInvocation();
        List<DataFlowBinding> acquired = new ArrayList<DataFlowBinding>();
        long scanned = 0L;
        long matched = 0L;
        String failureCode = "";
        try {
            checkDeadlineAndCancellation("dataflow.preflight");
            TreeMap<Long, DataFlowBinding> canonical = resolveAndValidate();
            for (DataFlowBinding binding : canonical.values()) {
                binding.acquire("dataflow.execute");
                acquired.add(binding);
            }
            state = State.RUNNING;
            checkDeadlineAndCancellation("dataflow.execute");
            DataFlowBinding candidate =
                    bindings.get(template.definition().candidateSource());
            int cardinality = candidate.packedSize();
            scanned = cardinality;
            matched = cardinality;
            if (budget.maximumOutputElements() < 1L) {
                throw DataFlowFailures.resource(
                        "dataflow_output_budget_exceeded",
                        template.definition().candidateSource().alias(),
                        "dataflow.execute", "1");
            }
            R result = executeTerminal(cardinality);
            state = State.COMPLETED;
            stats = stats(started, scanned, matched, 1L, "SUCCESS", "", 1, 1);
            return result;
        } catch (SomaRuntimeException failure) {
            failureCode = failure.code();
            if (state != State.CANCELLED) {
                state = State.FAILED;
            }
            stats = stats(started, scanned, matched, 0L,
                    state.name(), failureCode, 0, 0);
            throw failure;
        } catch (RuntimeException failure) {
            state = State.FAILED;
            failureCode = "dataflow_unexpected_failure";
            stats = stats(started, scanned, matched, 0L,
                    state.name(), failureCode, 0, 0);
            throw DataFlowFailures.callback(
                    failureCode, template.identity(), "dataflow.execute", failure);
        } finally {
            SomaRuntimeException cleanup = null;
            for (int index = acquired.size() - 1; index >= 0; index--) {
                try {
                    acquired.get(index).release(
                            "dataflow.execute",
                            state == State.COMPLETED,
                            scanned,
                            matched,
                            failureCode);
                } catch (SomaRuntimeException failure) {
                    if (cleanup == null) {
                        cleanup = failure;
                    } else {
                        cleanup.addSuppressed(failure);
                    }
                }
            }
            context.endInvocation();
            if (cleanup != null && state == State.COMPLETED) {
                state = State.FAILED;
                throw cleanup;
            }
        }
    }

    public DataFlowStats stats() {
        if (stats == null) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_stats_unavailable", template.identity(),
                    "dataflow.stats", state.name());
        }
        return stats;
    }

    public String state() {
        return state.name();
    }

    @SuppressWarnings("unchecked")
    private R executeTerminal(int cardinality) {
        if (template.definition().terminalKind()
                == DataFlowDefinition.TerminalKind.PACKED_COUNT) {
            return (R) new LongScalarResult(cardinality);
        }
        throw DataFlowFailures.internal(
                "dataflow_terminal_missing", template.identity(),
                "dataflow.execute", template.definition().terminalKind().name());
    }

    private TreeMap<Long, DataFlowBinding> resolveAndValidate() {
        List<SourceSlot<? extends DataFlowBinding>> required =
                template.definition().requiredSources();
        if (bindings.size() != required.size()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_binding_cardinality", template.identity(), "dataflow.bind");
        }
        TreeMap<Long, DataFlowBinding> canonical =
                new TreeMap<Long, DataFlowBinding>();
        IdentityHashMap<Object, Long> physicalIds = new IdentityHashMap<Object, Long>();
        for (SourceSlot<? extends DataFlowBinding> slot : required) {
            DataFlowBinding binding = bindings.get(slot);
            if (binding == null) {
                throw DataFlowFailures.invalidInput(
                        "dataflow_missing_binding", slot.alias(), "dataflow.bind");
            }
            verify(slot, binding);
            Long previousId = physicalIds.put(
                    binding.physicalIdentity(), Long.valueOf(binding.aggregateInstanceId()));
            if (previousId != null
                    && previousId.longValue() != binding.aggregateInstanceId()) {
                throw DataFlowFailures.conflict(
                        "dataflow_aggregate_identity_mismatch", slot.alias(),
                        "dataflow.bind", Long.toString(binding.aggregateInstanceId()));
            }
            DataFlowBinding previous =
                    canonical.put(Long.valueOf(binding.aggregateInstanceId()), binding);
            if (previous != null
                    && previous.physicalIdentity() != binding.physicalIdentity()) {
                throw DataFlowFailures.conflict(
                        "dataflow_aggregate_identity_collision", slot.alias(),
                        "dataflow.bind", Long.toString(binding.aggregateInstanceId()));
            }
        }
        return canonical;
    }

    private static void verify(
            SourceSlot<? extends DataFlowBinding> slot, DataFlowBinding binding) {
        requireIdentity(
                slot.schemaIdentity(), binding.schemaIdentity(),
                "dataflow_schema_mismatch", slot.alias());
        requireIdentity(
                slot.tableIdentity(), binding.tableIdentity(),
                "dataflow_table_mismatch", slot.alias());
        requireIdentity(
                "soma-generated-runtime-v5", binding.generatedProtocol(),
                "dataflow_generated_protocol_mismatch", slot.alias());
        requireIdentity(
                GeneratedDataFlow.TRANSFORMATION_PROTOCOL,
                binding.transformationProtocol(),
                "dataflow_transformation_protocol_mismatch", slot.alias());
        requireIdentity(
                GeneratedDataFlow.KERNEL_PROTOCOL,
                binding.kernelProtocol(),
                "dataflow_kernel_protocol_mismatch", slot.alias());
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
            state = State.CANCELLED;
            throw DataFlowFailures.lifecycle(
                    "dataflow_cancelled", template.identity(), operation, state.name());
        }
        long deadline = budget.deadlineNanos();
        if (deadline != 0L && System.nanoTime() - deadline >= 0L) {
            state = State.CANCELLED;
            throw DataFlowFailures.resource(
                    "dataflow_deadline_exceeded", template.identity(),
                    operation, Long.toString(deadline));
        }
    }

    private DataFlowStats stats(
            long started,
            long scanned,
            long matched,
            long output,
            String outcome,
            String failureCode,
            int tasks,
            int workers) {
        return new DataFlowStats(
                template.definition().identity(),
                template.identity(),
                policy.identity(),
                bindings.size(),
                scanned,
                matched,
                output,
                tasks,
                workers,
                System.nanoTime() - started,
                outcome,
                failureCode);
    }

    private void requireConfigurable(String operation) {
        if (state != State.NEW && state != State.BINDING) {
            throw DataFlowFailures.lifecycle(
                    "dataflow_invocation_consumed", template.identity(),
                    operation, state.name());
        }
    }
}
