package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** One logical plan bound to one immutable published Table generation. */
final class BoundRowPlan {

    final LogicalRowPlan logical;
    final TableStateRoot root;
    final SomaOperation operation;
    final Object provenance;
    final IntLocatorBuffer relationSource;
    final IntLocatorBuffer parallelSource;

    BoundRowPlan(
            LogicalRowPlan logical,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance) {
        this(logical, root, operation, provenance, null, null);
    }

    BoundRowPlan(
            LogicalRowPlan logical,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance,
            IntLocatorBuffer relationSource) {
        this(logical, root, operation, provenance, relationSource, null);
    }

    private BoundRowPlan(
            LogicalRowPlan logical,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance,
            IntLocatorBuffer relationSource,
            IntLocatorBuffer parallelSource) {
        if (logical == null || root == null || operation == null || provenance == null) {
            throw new AssertionError("invalid bound row plan");
        }
        this.logical = logical;
        this.root = root;
        this.operation = operation;
        this.provenance = provenance;
        this.relationSource = relationSource;
        this.parallelSource = parallelSource;
    }

    BoundRowPlan withParallelSource(IntLocatorBuffer source) {
        return new BoundRowPlan(
                logical, root, operation, provenance, relationSource, source);
    }

    long outputUpperBound() {
        long sourceUpperBound = root.size;
        if (relationSource != null) {
            sourceUpperBound = relationSource.size();
        } else if (logical.sourceKind() == LogicalRowPlan.SourceKind.INDEX_SELECTION) {
            sourceUpperBound = root.indexes[logical.indexOrdinal()].count(
                    root.directory, logical.indexProbe());
        }
        return logical.outputUpperBound(
                sourceUpperBound,
                new LogicalRowPlan.CardinalityStatistics() {
                    @Override
                    public long distinctUpperBound(int fieldIndex) {
                        int ordinal = logical.owner().layout()
                                .indexOrdinalForField(fieldIndex);
                        if (ordinal >= 0) {
                            return root.indexes[ordinal].distinctCount();
                        }
                        if (logical.owner().layout().fieldKey(fieldIndex)) {
                            return root.size;
                        }
                        return -1L;
                    }
                });
    }
}
