package com.hgtech.soma.dataflow;

/** Immutable、可复用的 callback-scoped delivery Template。 */
public final class CallbackDeliveryTemplate<V> {
    private final DataFlowTemplate<DeliveryResult> template;
    private final ParameterSlot<V> visitorSlot;

    CallbackDeliveryTemplate(
            DataFlowTemplate<DeliveryResult> template,
            ParameterSlot<V> visitorSlot) {
        this.template = template;
        this.visitorSlot = visitorSlot;
    }

    public String identity() {
        return template.identity();
    }

    public CallbackDeliveryInvocation<V> newInvocation(
            DataFlowContext context) {
        return new CallbackDeliveryInvocation<V>(
                template.newInvocation(context), visitorSlot);
    }

    public DataFlowExplain explain() {
        return template.explain();
    }
}
