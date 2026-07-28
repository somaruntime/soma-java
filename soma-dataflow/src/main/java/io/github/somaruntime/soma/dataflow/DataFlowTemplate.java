package io.github.somaruntime.soma.dataflow;

/**
 * Immutable analyzed and statically lowered DataFlow candidate.
 *
 * <p>A template is reusable and remains unbound to live Table state.</p>
 */
public final class DataFlowTemplate<R> {
    static final String PLANNER_POLICY = "soma-planner-v4";

    private final DataFlowDefinition<R> definition;
    private final String identity;

    DataFlowTemplate(DataFlowDefinition<R> definition) {
        this.definition = definition;
        identity = DataFlowSupport.identity(
                "soma-template-v1\n"
                        + definition.identity() + "\n"
                        + GeneratedDataFlow.TRANSFORMATION_PROTOCOL + "\n"
                        + GeneratedDataFlow.KERNEL_PROTOCOL + "\n"
                        + PLANNER_POLICY + "\n"
                        + CandidatePhysicalFormula.IDENTITY + "\n"
                        + RelationStrategyFormula.IDENTITY + "\n"
                        + MorselSchedulerFormula.IDENTITY + "\n"
                        + InvocationLedger.IDENTITY + "\n");
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
                definition.operation().logicalShape(),
                definition.operation().logicalPlan(),
                definition.operation().physicalPlan());
    }

    DataFlowDefinition<R> definition() {
        return definition;
    }
}
