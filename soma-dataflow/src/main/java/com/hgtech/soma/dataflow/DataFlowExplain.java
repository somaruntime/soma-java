package com.hgtech.soma.dataflow;

/** Detached, redacted and non-hot-path logical/physical explanation. */
public final class DataFlowExplain {
    private final String definitionIdentity;
    private final String templateIdentity;
    private final String logicalShape;
    private final String logicalPlan;
    private final String physicalPlan;
    private final boolean bound;
    private final long boundSources;
    private final long boundCardinality;
    private final String parallelDecision;
    private final String fallbackReason;
    private final String parameterSummary;
    private final String budgetSummary;
    private final String candidatePhysicalFormulaIdentity;
    private final String relationStrategyFormulaIdentity;
    private final String schedulerFormulaIdentity;
    private final String invocationLedgerIdentity;

    DataFlowExplain(
            String definitionIdentity,
            String templateIdentity,
            String logicalShape,
            String logicalPlan,
            String physicalPlan) {
        this(
                definitionIdentity,
                templateIdentity,
                logicalShape,
                logicalPlan,
                physicalPlan,
                false,
                0L,
                -1L,
                "unbound",
                "unbound",
                "unbound",
                "unbound");
    }

    DataFlowExplain(
            String definitionIdentity,
            String templateIdentity,
            String logicalShape,
            String logicalPlan,
            String physicalPlan,
            boolean bound,
            long boundSources,
            long boundCardinality,
            String parallelDecision,
            String fallbackReason,
            String parameterSummary,
            String budgetSummary) {
        this.definitionIdentity = definitionIdentity;
        this.templateIdentity = templateIdentity;
        this.logicalShape = logicalShape;
        this.logicalPlan = logicalPlan;
        this.physicalPlan = physicalPlan;
        this.bound = bound;
        this.boundSources = boundSources;
        this.boundCardinality = boundCardinality;
        this.parallelDecision = parallelDecision;
        this.fallbackReason = fallbackReason;
        this.parameterSummary = parameterSummary;
        this.budgetSummary = budgetSummary;
        candidatePhysicalFormulaIdentity =
                CandidatePhysicalFormula.IDENTITY;
        relationStrategyFormulaIdentity =
                RelationStrategyFormula.IDENTITY;
        schedulerFormulaIdentity = MorselSchedulerFormula.IDENTITY;
        invocationLedgerIdentity = InvocationLedger.IDENTITY;
    }

    public String definitionIdentity() { return definitionIdentity; }
    public String templateIdentity() { return templateIdentity; }
    public String logicalShape() { return logicalShape; }
    public String logicalPlan() { return logicalPlan; }
    public String physicalPlan() { return physicalPlan; }
    public boolean bound() { return bound; }
    public long boundSources() { return boundSources; }
    public long boundCardinality() { return boundCardinality; }
    public String parallelDecision() { return parallelDecision; }
    public String fallbackReason() { return fallbackReason; }
    public String parameterSummary() { return parameterSummary; }
    public String budgetSummary() { return budgetSummary; }
    public String candidatePhysicalFormulaIdentity() {
        return candidatePhysicalFormulaIdentity;
    }
    public String relationStrategyFormulaIdentity() {
        return relationStrategyFormulaIdentity;
    }
    public String schedulerFormulaIdentity() {
        return schedulerFormulaIdentity;
    }
    public String invocationLedgerIdentity() {
        return invocationLedgerIdentity;
    }
}
