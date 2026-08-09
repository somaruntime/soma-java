package io.github.somaruntime.soma.internal;

import io.github.somaruntime.soma.SomaOperation;

/** One logical plan bound to one immutable published Table generation. */
final class BoundRowPlan {

    final LogicalRowPlan logical;
    final TableStateRoot root;
    final SomaOperation operation;
    final Object provenance;

    BoundRowPlan(
            LogicalRowPlan logical,
            TableStateRoot root,
            SomaOperation operation,
            Object provenance) {
        if (logical == null || root == null || operation == null || provenance == null) {
            throw new AssertionError("invalid bound row plan");
        }
        this.logical = logical;
        this.root = root;
        this.operation = operation;
        this.provenance = provenance;
    }

    long outputUpperBound() {
        return logical.outputUpperBound(root.size);
    }
}
