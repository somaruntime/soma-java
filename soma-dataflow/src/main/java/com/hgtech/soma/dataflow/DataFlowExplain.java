package com.hgtech.soma.dataflow;

/** Detached, redacted and non-hot-path logical/physical explanation. */
public final class DataFlowExplain {
    private final String definitionIdentity;
    private final String templateIdentity;
    private final String logicalShape;
    private final String logicalPlan;
    private final String physicalPlan;

    DataFlowExplain(
            String definitionIdentity,
            String templateIdentity,
            String logicalShape,
            String logicalPlan,
            String physicalPlan) {
        this.definitionIdentity = definitionIdentity;
        this.templateIdentity = templateIdentity;
        this.logicalShape = logicalShape;
        this.logicalPlan = logicalPlan;
        this.physicalPlan = physicalPlan;
    }

    public String definitionIdentity() { return definitionIdentity; }
    public String templateIdentity() { return templateIdentity; }
    public String logicalShape() { return logicalShape; }
    public String logicalPlan() { return logicalPlan; }
    public String physicalPlan() { return physicalPlan; }
}
