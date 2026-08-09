package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** One logical plan bound to one immutable published Table generation. */
final class BoundRowPlan {

    final LogicalRowPlan logical;
    final TableStateRoot root;
    final SomaOperation operation;
    final Object provenance;
    final LongLocatorBuffer relationSource;
    final LongLocatorBuffer parallelSource;

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
            LongLocatorBuffer relationSource) {
        this(logical, root, operation, provenance, relationSource, null);
    }

    private BoundRowPlan(
            LogicalRowPlan logical,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance,
            LongLocatorBuffer relationSource,
            LongLocatorBuffer parallelSource) {
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

    BoundRowPlan withParallelSource(LongLocatorBuffer source) {
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
