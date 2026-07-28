package io.github.somaruntime.soma.dataflow;

/**
 * 可复用、与 visitor instance 无关的 callback-scoped delivery Definition。
 *
 * @param <V> typed visitor protocol
 */
public final class CallbackDeliveryDefinition<V> {
    private final DataFlowDefinition<DeliveryResult> definition;
    private final ParameterSlot<V> visitorSlot;

    CallbackDeliveryDefinition(
            DataFlowDefinition<DeliveryResult> definition,
            ParameterSlot<V> visitorSlot) {
        if (definition == null) throw new NullPointerException("definition");
        if (visitorSlot == null) throw new NullPointerException("visitorSlot");
        this.definition = definition;
        this.visitorSlot = visitorSlot;
    }

    static <V> CallbackDeliveryDefinition<V> of(
            DataFlowOperation<DeliveryResult> operation,
            ParameterSlot<V> visitorSlot) {
        return new CallbackDeliveryDefinition<V>(
                DataFlowDefinition.of(operation), visitorSlot);
    }

    public String identity() {
        return definition.identity();
    }

    public CallbackDeliveryTemplate<V> compile() {
        return new CallbackDeliveryTemplate<V>(
                definition.compile(), visitorSlot);
    }

    public DataFlowExplain explain() {
        return definition.explain();
    }
}
