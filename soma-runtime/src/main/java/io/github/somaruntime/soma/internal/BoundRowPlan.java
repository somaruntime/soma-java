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
        return logical.outputUpperBound(root.size);
    }
}
