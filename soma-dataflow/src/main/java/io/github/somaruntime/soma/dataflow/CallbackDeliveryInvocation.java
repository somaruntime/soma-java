package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;

/**
 * One-shot callback-scoped delivery Invocation。
 *
 * <p>Visitor 只属于本次 Invocation，不进入 Definition/Template identity。</p>
 */
public final class CallbackDeliveryInvocation<V> {
    private final DataFlowInvocation<DeliveryResult> invocation;
    private final ParameterSlot<V> visitorSlot;

    CallbackDeliveryInvocation(
            DataFlowInvocation<DeliveryResult> invocation,
            ParameterSlot<V> visitorSlot) {
        this.invocation = invocation;
        this.visitorSlot = visitorSlot;
    }

    public <B extends DataFlowBinding> CallbackDeliveryInvocation<V> bind(
            SourceSlot<B> slot, B binding) {
        invocation.bind(slot, binding);
        return this;
    }

    public <T> CallbackDeliveryInvocation<V> parameter(
            ParameterSlot<T> slot, T value) {
        invocation.parameter(slot, value);
        return this;
    }

    public CallbackDeliveryInvocation<V> visitor(V value) {
        invocation.parameter(visitorSlot, value);
        return this;
    }

    public CallbackDeliveryInvocation<V> policy(ExecutionPolicy value) {
        invocation.policy(value);
        return this;
    }

    public CallbackDeliveryInvocation<V> budget(ExecutionBudget value) {
        invocation.budget(value);
        return this;
    }

    public CallbackDeliveryInvocation<V> cancellationToken(
            CancellationToken value) {
        invocation.cancellationToken(value);
        return this;
    }

    public DataFlowExplain explain() {
        return invocation.explain();
    }

    public DeliveryResult execute() {
        return invocation.execute();
    }

    public DataFlowStats stats() {
        return invocation.stats();
    }

    public String state() {
        return invocation.state();
    }
}
