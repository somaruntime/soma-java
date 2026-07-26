package com.hgtech.soma.dataflow;

/**
 * Immutable analyzed and statically lowered DataFlow candidate.
 *
 * <p>A template is reusable and remains unbound to live Table state.</p>
 */
public final class DataFlowTemplate<R> {
    static final String PLANNER_POLICY = "soma-planner-v1";

    private final DataFlowDefinition<R> definition;
    private final String identity;

    DataFlowTemplate(DataFlowDefinition<R> definition) {
        this.definition = definition;
        identity = DataFlowSupport.identity(
                "soma-template-v1\n"
                        + definition.identity() + "\n"
                        + GeneratedDataFlow.TRANSFORMATION_PROTOCOL + "\n"
                        + GeneratedDataFlow.KERNEL_PROTOCOL + "\n"
                        + PLANNER_POLICY + "\n");
    }

    public String identity() {
        return identity;
    }

    public DataFlowInvocation<R> newInvocation(DataFlowContext context) {
        return new DataFlowInvocation<R>(this, context);
    }

    public DataFlowExplain explain() {
        return new DataFlowExplain(
                definition.identity(),
                identity,
                "Candidate -> Scalar<long>",
                definition.candidateSource().alias() + ":Packed -> Count",
                "candidate-program[packed-count]");
    }

    DataFlowDefinition<R> definition() {
        return definition;
    }
}
