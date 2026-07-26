package com.hgtech.soma.dataflow;

import com.hgtech.soma.dataflow.generated.DataFlowBinding;
import com.hgtech.soma.dataflow.generated.OwnedChildAccess;

interface ExpandedVisitor<C extends DataFlowBinding> {
    boolean accept(
            int parentIndex,
            C childBinding,
            int childIndex,
            int outputPosition);
}

final class ExpandedVisit {
    final long scanned;
    final int matched;

    ExpandedVisit(long scanned, int matched) {
        this.scanned = scanned;
        this.matched = matched;
    }
}

final class ExpandedProgram<
        P extends DataFlowBinding, C extends DataFlowBinding> {
    private final CandidateProgram<P> parents;
    private final SourceSlot<C> childSource;
    private final OwnedChildAccess<P, C> access;
    private final BooleanExpression<C> childPredicate;
    private final String canonical;

    ExpandedProgram(
            CandidateProgram<P> parents,
            SourceSlot<C> childSource,
            OwnedChildAccess<P, C> access,
            BooleanExpression<C> childPredicate) {
        this.parents = parents;
        this.childSource = childSource;
        this.access = access;
        this.childPredicate = childPredicate;
        canonical = parents.canonical()
                + "->owned-expand("
                + childSource.ordinal() + ","
                + childSource.alias() + ","
                + childSource.schemaIdentity() + ","
                + childSource.tableIdentity() + ","
                + access.identity() + ")"
                + (childPredicate == null ? ""
                : "->child-filter(" + childPredicate.identity() + ")");
    }

    SourceSlot<P> parentSource() {
        return parents.source();
    }

    SourceSlot<C> childSource() {
        return childSource;
    }

    String canonical() {
        return canonical;
    }

    ExpandedProgram<P, C> filterChild(
            BooleanExpression<C> predicate) {
        BooleanExpression<C> combined = childPredicate == null
                ? predicate : childPredicate.and(predicate);
        return new ExpandedProgram<P, C>(
                parents, childSource, access, combined);
    }

    ExpandedVisit visit(
            ExecutionFrame frame,
            final ExpandedVisitor<C> visitor,
            String operation) {
        @SuppressWarnings("unchecked")
        final P parentBinding =
                (P) frame.binding(parents.source());
        final long[] childScanned = new long[1];
        final int[] matched = new int[1];
        CandidateVisit parentVisit = parents.visit(
                frame,
                new CandidateVisitor() {
                    @Override
                    public boolean accept(
                            int parentIndex, int outputPosition) {
                        C childBinding = access.childBinding(
                                parentBinding, parentIndex);
                        if (childBinding == null) {
                            return true;
                        }
                        int childSize = childBinding.packedSize();
                        for (int childIndex = 0;
                             childIndex < childSize;
                             childIndex++) {
                            childScanned[0]++;
                            if (childPredicate != null
                                    && !childPredicate.evaluate(
                                    childBinding, childIndex)) {
                                continue;
                            }
                            int expandedPosition = matched[0]++;
                            if (!visitor.accept(
                                    parentIndex,
                                    childBinding,
                                    childIndex,
                                    expandedPosition)) {
                                return false;
                            }
                        }
                        return true;
                    }
                },
                operation);
        return new ExpandedVisit(
                parentVisit.scanned + childScanned[0], matched[0]);
    }
}

abstract class ExpandedOperation<
        P extends DataFlowBinding,
        C extends DataFlowBinding,
        R> extends SingleSourceOperation<R> {
    final ExpandedProgram<P, C> program;

    ExpandedOperation(ExpandedProgram<P, C> program) {
        super(program.parentSource());
        this.program = program;
    }

    @Override
    public final String logicalPlan() {
        return program.canonical() + " -> " + terminal();
    }

    @Override
    public final String physicalPlan() {
        return "owned-aggregate[parent-candidate,child-packed,"
                + terminal() + "]";
    }

    abstract String terminal();
}

final class ExpandedCountOperation<
        P extends DataFlowBinding, C extends DataFlowBinding>
        extends ExpandedOperation<P, C, LongScalarResult> {
    private static final ExpandedVisitor<DataFlowBinding> NOOP =
            new ExpandedVisitor<DataFlowBinding>() {
                @Override
                public boolean accept(
                        int parentIndex,
                        DataFlowBinding childBinding,
                        int childIndex,
                        int outputPosition) {
                    return true;
                }
            };

    ExpandedCountOperation(ExpandedProgram<P, C> program) {
        super(program);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->count";
    }

    @Override
    public String logicalShape() {
        return "Expanded<Parent,OwnedChild> -> Scalar<long>";
    }

    @Override
    String terminal() {
        return "count";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(
            ExecutionFrame frame) {
        @SuppressWarnings("unchecked")
        ExpandedVisitor<C> visitor =
                (ExpandedVisitor<C>) (ExpandedVisitor<?>) NOOP;
        ExpandedVisit visit =
                program.visit(frame, visitor, "dataflow.expand.count");
        frame.reserveOutput(1L, 8L, "dataflow.expand.count");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(visit.matched),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}

final class ExpandedIndexOperation<
        P extends DataFlowBinding, C extends DataFlowBinding>
        extends ExpandedOperation<P, C, ExpandedIndexResult> {
    ExpandedIndexOperation(ExpandedProgram<P, C> program) {
        super(program);
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->indexes";
    }

    @Override
    public String logicalShape() {
        return "Expanded<Parent,OwnedChild> -> DetachedCurrentIndexes";
    }

    @Override
    String terminal() {
        return "detached-current-indexes";
    }

    @Override
    public ExecutionOutcome<ExpandedIndexResult> execute(
            final ExecutionFrame frame) {
        ExpandedVisit counted = program.visit(
                frame,
                new ExpandedVisitor<C>() {
                    @Override
                    public boolean accept(
                            int parentIndex,
                            C childBinding,
                            int childIndex,
                            int outputPosition) {
                        return true;
                    }
                },
                "dataflow.expand.indexes.preflight");
        final int[] parents = frame.newOutputIndexes(
                counted.matched, "dataflow.expand.indexes");
        final int[] children = frame.newOutputIndexes(
                counted.matched, "dataflow.expand.indexes");
        ExpandedVisit filled = program.visit(
                frame,
                new ExpandedVisitor<C>() {
                    @Override
                    public boolean accept(
                            int parentIndex,
                            C childBinding,
                            int childIndex,
                            int outputPosition) {
                        parents[outputPosition] = parentIndex;
                        children[outputPosition] = childIndex;
                        return true;
                    }
                },
                "dataflow.expand.indexes");
        if (filled.matched != counted.matched) {
            throw DataFlowFailures.internal(
                    "dataflow_expand_cardinality_changed",
                    program.parentSource().alias(),
                    "dataflow.expand.indexes",
                    counted.matched + "/" + filled.matched);
        }
        return new ExecutionOutcome<ExpandedIndexResult>(
                new ExpandedIndexResult(
                        parents, children, filled.matched),
                filled.scanned,
                filled.matched,
                filled.matched,
                1,
                1);
    }
}

final class ExpandedLongColumnOperation<
        P extends DataFlowBinding, C extends DataFlowBinding>
        extends ExpandedOperation<P, C, LongColumnResult> {
    private final LongExpression<C> expression;

    ExpandedLongColumnOperation(
            ExpandedProgram<P, C> program,
            LongExpression<C> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->long-column("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Expanded<Parent,OwnedChild> -> DetachedColumn<long>";
    }

    @Override
    String terminal() {
        return "detached-column<long>";
    }

    @Override
    public ExecutionOutcome<LongColumnResult> execute(
            final ExecutionFrame frame) {
        ExpandedVisit counted = program.visit(
                frame,
                new ExpandedVisitor<C>() {
                    @Override
                    public boolean accept(
                            int parentIndex,
                            C childBinding,
                            int childIndex,
                            int outputPosition) {
                        return true;
                    }
                },
                "dataflow.expand.longColumn.preflight");
        final long[] values = frame.newOutputLongs(
                counted.matched, "dataflow.expand.longColumn");
        ExpandedVisit filled = program.visit(
                frame,
                new ExpandedVisitor<C>() {
                    @Override
                    public boolean accept(
                            int parentIndex,
                            C childBinding,
                            int childIndex,
                            int outputPosition) {
                        values[outputPosition] =
                                expression.evaluate(
                                        childBinding, childIndex);
                        return true;
                    }
                },
                "dataflow.expand.longColumn");
        if (filled.matched != counted.matched) {
            throw DataFlowFailures.internal(
                    "dataflow_expand_cardinality_changed",
                    program.parentSource().alias(),
                    "dataflow.expand.longColumn",
                    counted.matched + "/" + filled.matched);
        }
        return new ExecutionOutcome<LongColumnResult>(
                new LongColumnResult(values, filled.matched),
                filled.scanned,
                filled.matched,
                filled.matched,
                1,
                1);
    }
}

final class ExpandedLongSumOperation<
        P extends DataFlowBinding, C extends DataFlowBinding>
        extends ExpandedOperation<P, C, LongScalarResult> {
    private final LongExpression<C> expression;

    ExpandedLongSumOperation(
            ExpandedProgram<P, C> program,
            LongExpression<C> expression) {
        super(program);
        this.expression = expression;
    }

    @Override
    public String canonicalForm() {
        return program.canonical() + "->long-sum("
                + expression.identity() + ")";
    }

    @Override
    public String logicalShape() {
        return "Expanded<Parent,OwnedChild> -> Scalar<long>";
    }

    @Override
    String terminal() {
        return "long-sum";
    }

    @Override
    public ExecutionOutcome<LongScalarResult> execute(
            final ExecutionFrame frame) {
        final long[] sum = new long[1];
        ExpandedVisit visit = program.visit(
                frame,
                new ExpandedVisitor<C>() {
                    @Override
                    public boolean accept(
                            int parentIndex,
                            C childBinding,
                            int childIndex,
                            int outputPosition) {
                        sum[0] += expression.evaluate(
                                childBinding, childIndex);
                        return true;
                    }
                },
                "dataflow.expand.longSum");
        frame.reserveOutput(1L, 8L, "dataflow.expand.longSum");
        return new ExecutionOutcome<LongScalarResult>(
                new LongScalarResult(sum[0]),
                visit.scanned,
                visit.matched,
                1L,
                1,
                1);
    }
}
