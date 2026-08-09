package io.github.somaruntime.soma.internal;

/** One logical plan bound to one immutable published Table generation. */
final class BoundRowPlan {

    final LogicalRowPlan logical;
    final TableStateRoot root;
    final Object provenance;

    BoundRowPlan(LogicalRowPlan logical, TableStateRoot root, Object provenance) {
        if (logical == null || root == null || provenance == null) {
            throw new AssertionError("invalid bound row plan");
        }
        this.logical = logical;
        this.root = root;
        this.provenance = provenance;
    }

    long outputUpperBound() {
        return logical.outputUpperBound(root.size);
    }
}
