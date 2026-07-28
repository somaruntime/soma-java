package io.github.somaruntime.soma.dataflow;

import io.github.somaruntime.soma.dataflow.generated.DataFlowBinding;
import io.github.somaruntime.soma.dataflow.generated.OwnedChildAccess;

/**
 * Lazy parent-to-owned-child expansion retaining both current-index lineages.
 */
public final class ExpandedFlow<
        P extends DataFlowBinding, C extends DataFlowBinding> {
    private final ExpandedProgram<P, C> program;

    ExpandedFlow(
            CandidateProgram<P> parents,
            SourceSlot<C> childSource,
            OwnedChildAccess<P, C> access) {
        this(new ExpandedProgram<P, C>(
                parents, childSource, access, null));
    }

    private ExpandedFlow(ExpandedProgram<P, C> program) {
        this.program = program;
    }

    public ExpandedFlow<P, C> filterChild(
            BooleanExpression<C> predicate) {
        requireChildSource(
                predicate == null ? null : predicate.source, "predicate");
        return new ExpandedFlow<P, C>(program.filterChild(predicate));
    }

    public DataFlowDefinition<LongScalarResult> count() {
        return DataFlowDefinition.of(
                new ExpandedCountOperation<P, C>(program));
    }

    public DataFlowDefinition<ExpandedIndexResult> indexes() {
        return DataFlowDefinition.of(
                new ExpandedIndexOperation<P, C>(program));
    }

    public LongExpandedValueFlow<P, C> projectChild(
            LongExpression<C> expression) {
        requireChildSource(
                expression == null ? null : expression.source, "expression");
        return new LongExpandedValueFlow<P, C>(program, expression);
    }

    private void requireChildSource(
            SourceSlot<?> source, String name) {
        if (source == null) {
            throw new NullPointerException(name);
        }
        if (source != program.childSource()) {
            throw DataFlowFailures.invalidInput(
                    "dataflow_owned_child_source_mismatch",
                    program.childSource().alias(),
                    "dataflow.expand." + name);
        }
    }
}
